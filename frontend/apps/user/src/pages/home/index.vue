<template>
  <div class="page">
    <van-search v-model="keyword" placeholder="搜索品牌 / 型号 / 车牌" @search="reload" />

    <!-- 门店地图：标记为门店位置，点击标记可只看该门店的车 -->
    <MapView
      :markers="markers"
      :center="mapCenter"
      :radius-km="nearbyActive ? nearbyRadius : 0"
      @marker-click="onMarkerClick"
    />

    <van-dropdown-menu>
      <van-dropdown-item v-model="mode" :options="modeOptions" @change="reload" />
    </van-dropdown-menu>

    <div class="loc-bar muted">
      <span v-if="locating">正在获取当前位置…</span>
      <span v-else-if="myLocation">
        已定位 · 附近 {{ nearbyRadius }}km 内共 {{ cars.length }} 辆可租车辆
      </span>
      <span v-else>未获取到定位，正在显示全部门店的可租车辆</span>
    </div>

    <div v-if="selectedStore" class="store-bar" @click="clearStore">
      <span>📍 只看「{{ selectedStore.name }}」的车辆</span>
      <van-icon name="cross" />
    </div>

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <van-list
        v-model:loading="loading"
        :finished="finished"
        finished-text="没有更多了"
        @load="onLoad"
      >
        <van-empty
          v-if="!loading && cars.length === 0"
          image="search"
          :description="emptyText"
        >
          <van-button v-if="nearbyActive" size="small" type="primary" @click="showAll">
            查看全部车辆
          </van-button>
        </van-empty>

        <div
          v-for="car in cars"
          :key="car.carId"
          class="card-block car-card"
          @click="toDetail(car.carId)"
        >
          <div class="car-cover">🚘</div>
          <div class="row-between">
            <div class="car-title">{{ car.brand }} {{ car.model || '' }}</div>
            <van-tag type="success">可租</van-tag>
          </div>
          <div class="muted mt6">
            {{ car.plateNo }} · {{ carTypeText[car.type] || '-' }} ·
            {{ fuelTypeText[car.fuelType] || '-' }}
          </div>
          <div class="muted mt4">
            <span v-if="car.storeName">📍 {{ car.storeName }}</span>
            <span v-else>📍 门店待分配</span>
            <span v-if="car.distance != null" class="distance">（距你 {{ formatDistance(car.distance) }}）</span>
          </div>
          <div class="row-between mt8">
            <div><span class="price">¥{{ car.dailyPrice }}</span><span class="muted"> /天</span></div>
            <div class="muted">押金 ¥{{ car.deposit || 0 }}</div>
          </div>
        </div>
      </van-list>
    </van-pull-refresh>
  </div>
</template>

<script setup>
import { computed, ref, onMounted, watch } from 'vue'
import { showToast } from 'vant'
import uni from '@shared/utils/uni'
import { carApi, storeApi } from '@shared/api'
import MapView from '@shared/components/MapView.vue'
import { carTypeText, fuelTypeText } from '@shared/utils/dict'
import { distanceKm, formatDistance, getCurrentPosition } from '@shared/utils/geo'
import { NEARBY_RADIUS_KM } from '@shared/config/map'

const nearbyRadius = NEARBY_RADIUS_KM
const modeOptions = [
  { text: '📍 附近可租车辆', value: 'nearby' },
  { text: '全部可租车辆', value: 'all' }
]

const keyword = ref('')
const mode = ref('nearby')

const stores = ref([])
const cars = ref([])
const selectedStoreId = ref(null)

const pageNum = ref(1)
const pageSize = 10
const loading = ref(false)
const finished = ref(false)
const refreshing = ref(false)

const myLocation = ref(null)
const locating = ref(false)

const nearbyActive = computed(() => mode.value === 'nearby' && !!myLocation.value)
const mapCenter = computed(() =>
  myLocation.value ? [myLocation.value.longitude, myLocation.value.latitude] : null
)

/** 地图上展示门店（只有配了经纬度的才会画出来） */
const markers = computed(() =>
  stores.value.map((s) => ({
    id: s.storeId,
    title: `${s.name}（可租 ${s.rentableCarCount ?? 0} 辆）`,
    longitude: s.longitude,
    latitude: s.latitude
  }))
)

const selectedStore = computed(
  () => stores.value.find((s) => s.storeId === selectedStoreId.value) || null
)

const emptyText = computed(() => {
  if (nearbyActive.value) return '当前位置附近没有可租车辆'
  if (selectedStoreId.value) return '该门店暂无可租车辆'
  return '暂无可租车辆'
})

/** 计算车辆到「我的位置」的距离（用车辆所属门店的坐标） */
function withStoreAndDistance(list) {
  return list.map((car) => {
    const store = stores.value.find((s) => s.storeId === car.storeId)
    let distance = null
    if (myLocation.value && store && store.longitude != null && store.latitude != null) {
      distance = distanceKm(
        myLocation.value.longitude,
        myLocation.value.latitude,
        Number(store.longitude),
        Number(store.latitude)
      )
    }
    return { ...car, storeName: store ? store.name : '', distance }
  })
}

async function loadStores() {
  try {
    stores.value = await storeApi.list()
  } catch (e) {
    stores.value = []
  }
}

async function fetchCars(append) {
  let records = []

  if (selectedStoreId.value) {
    // 选中门店：直接取该门店的可租车辆列表
    records = await storeApi.rentableCars(selectedStoreId.value)
    finished.value = true
  } else {
    const data = await carApi.page({
      pageNum: nearbyActive.value ? 1 : pageNum.value,
      pageSize: nearbyActive.value ? 100 : pageSize,
      keyword: keyword.value,
      // 首页只展示可租用（空闲）的车辆
      status: 'FREE'
    })
    records = data.records || []
    if (!nearbyActive.value) {
      pageNum.value += 1
      finished.value = records.length < pageSize || (append ? cars.value.length + records.length : records.length) >= (data.total || 0)
    }
  }

  let list = withStoreAndDistance(records)
  if (nearbyActive.value) {
    list = list
      .filter((car) => car.distance != null && car.distance <= nearbyRadius)
      .sort((a, b) => a.distance - b.distance)
    finished.value = true
  }
  cars.value = append ? cars.value.concat(list) : list
}

async function onLoad() {
  if (finished.value) {
    loading.value = false
    return
  }
  try {
    await fetchCars(!finished.value && cars.value.length > 0 && !nearbyActive.value)
  } catch (e) {
    showToast(e.message || '加载失败')
    finished.value = true
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

async function reload() {
  pageNum.value = 1
  cars.value = []
  finished.value = false
  loading.value = true
  await onLoad()
}

async function onRefresh() {
  await loadStores()
  await reload()
}

function showAll() {
  mode.value = 'all'
  reload()
}

function onMarkerClick(storeId) {
  selectedStoreId.value = storeId
  reload()
}

function clearStore() {
  selectedStoreId.value = null
  reload()
}

function toDetail(id) {
  uni.navigateTo(`/pages/car/detail?id=${id}`)
}

async function locate() {
  locating.value = true
  try {
    myLocation.value = await getCurrentPosition()
  } catch (e) {
    mode.value = 'all'
    showToast(`${e.message}，已切换为全部车辆`)
  } finally {
    locating.value = false
  }
}

watch(myLocation, () => {
  if (mode.value === 'nearby' && !selectedStoreId.value) reload()
})

onMounted(async () => {
  await loadStores()
  await reload()
  await locate()
})
</script>

<style scoped>
.car-card {
  cursor: pointer;
}

.row-between {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.mt4 {
  margin-top: 4px;
}

.mt6 {
  margin-top: 6px;
}

.mt8 {
  margin-top: 8px;
}

.distance {
  color: #1989fa;
  margin-left: 4px;
}

.loc-bar {
  padding: 8px 16px 0;
  font-size: 12px;
}

.store-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 8px 12px 0;
  padding: 8px 12px;
  border-radius: 8px;
  background: #eef3ff;
  color: #1989fa;
  font-size: 13px;
}
</style>
