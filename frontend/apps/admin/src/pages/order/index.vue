<template>
  <div class="page">
    <van-tabs v-model:active="activeIndex" @change="reload">
      <van-tab v-for="t in tabs" :key="t.value" :title="t.label" />
    </van-tabs>

    <van-empty v-if="!loading && orders.length === 0" image="search" description="暂无订单" />

    <div
      v-for="(order, index) in orders"
      :key="order?.orderId ?? index"
      class="card-block"
    >
      <div class="row-between">
        <div style="font-size: 14px; font-weight: 600">{{ order?.orderNo }}</div>
        <van-tag :type="rentalStatusTag[order?.status]">
          {{ rentalStatusText[order?.status] }}
        </van-tag>
      </div>
      <div class="muted mt4">{{ carLabel(order?.carId) }} · 用户 #{{ order?.userId ?? '-' }}</div>
      <div class="muted mt4">
        {{ formatDateTime(order?.startTime) }} ~ {{ formatDateTime(order?.endTime) }}
      </div>
      <div class="row-between mt8">
        <div>
          <span class="price">¥{{ order?.totalAmount ?? 0 }}</span>
          <span class="muted">
            （租金 ¥{{ order?.rentAmount ?? 0 }} + 押金 ¥{{ order?.deposit || 0 }}）
          </span>
        </div>
      </div>
      <div class="item-actions" v-if="order?.status === 'RENTING' && auth.isAdmin">
        <van-button size="small" type="primary" @click="openReturn(order)">办理还车</van-button>
      </div>
    </div>

    <div v-if="orders.length" class="load-more">
      <span v-if="finished">共 {{ total }} 条，没有更多了</span>
      <van-button v-else size="small" plain type="primary" :loading="loading" @click="loadMore">
        加载更多
      </van-button>
    </div>

    <van-dialog
      v-model:show="returnVisible"
      title="办理还车"
      show-cancel-button
      @confirm="doReturn"
    >
      <van-field v-model="mileageAfter" type="digit" label="还车里程" placeholder="选填，单位 km" />
    </van-dialog>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { showToast } from 'vant'
import { carApi, rentalApi } from '@shared/api'
import { useAuthStore } from '@shared/store/auth'
import { rentalStatusText, rentalStatusTag, formatDateTime } from '@shared/utils/dict'

const auth = useAuthStore()
const tabs = [
  { label: '全部', value: '' },
  { label: '租用中', value: 'RENTING' },
  { label: '已还车', value: 'RETURNED' },
  { label: '已取消', value: 'CANCELLED' }
]
const activeIndex = ref(0)
const status = computed(() => (tabs[activeIndex.value] || tabs[0]).value)

const orders = ref([])
const carMap = ref({})
const pageNum = ref(1)
const pageSize = 10
const total = ref(0)
const loading = ref(false)
const finished = ref(false)

const returnVisible = ref(false)
const mileageAfter = ref('')
const returnTarget = ref(null)

const carLabel = (carId) => {
  if (carId == null) return '未知车辆'
  const car = carMap.value[carId]
  return car ? `${car.brand} ${car.model || ''} · ${car.plateNo}` : `车辆 #${carId}`
}

async function loadCars(list) {
  const ids = [...new Set(list.map((o) => o.carId))].filter((id) => !carMap.value[id])
  await Promise.all(
    ids.map(async (id) => {
      try {
        carMap.value[id] = await carApi.detail(id)
      } catch (e) {
        // 车辆可能已删除
      }
    })
  )
}

async function fetchPage(append = false) {
  loading.value = true
  try {
    const data = await rentalApi.page({
      pageNum: pageNum.value,
      pageSize,
      status: status.value
    })
    const records = data.records || []
    orders.value = append ? orders.value.concat(records) : records
    total.value = data.total || 0
    finished.value = orders.value.length >= total.value || records.length < pageSize
    await loadCars(orders.value)
  } catch (e) {
    showToast(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function reload() {
  pageNum.value = 1
  finished.value = false
  fetchPage(false)
}

function loadMore() {
  if (finished.value) return
  pageNum.value += 1
  fetchPage(true)
}

function openReturn(order) {
  returnTarget.value = order
  mileageAfter.value = ''
  returnVisible.value = true
}

async function doReturn() {
  const order = returnTarget.value
  if (!order) return
  const payload = mileageAfter.value === '' ? {} : { mileageAfter: Number(mileageAfter.value) }
  try {
    await rentalApi.returnCar(order.orderId, payload)
    showToast({ type: 'success', message: '还车成功' })
    reload()
  } catch (e) {
    showToast(e.message || '还车失败')
  }
}

onMounted(reload)
</script>
