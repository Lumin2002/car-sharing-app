<template>
  <div class="page" v-if="car">
    <div class="car-cover detail-cover">🚘</div>

    <van-cell-group inset title="车辆信息">
      <van-cell title="车辆" :value="`${car.brand} ${car.model || ''}`" />
      <van-cell title="车牌号" :value="car.plateNo" />
      <van-cell title="状态">
        <template #value>
          <van-tag :type="carStatusTag[car.status]">{{ carStatusText[car.status] }}</van-tag>
        </template>
      </van-cell>
      <van-cell title="日租金" :value="`¥${car.dailyPrice}`" />
      <van-cell title="押金" :value="`¥${car.deposit || 0}`" />
      <van-cell title="所属门店" :value="storeLabel" />
      <van-cell title="车型" :value="carTypeText[car.type] || '-'" />
      <van-cell title="能源" :value="fuelTypeText[car.fuelType] || '-'" />
      <van-cell title="颜色" :value="car.color || '-'" />
      <van-cell title="座位 / 车门" :value="`${car.seatNum || '-'} 座 / ${car.doorNum || '-'} 门`" />
      <van-cell title="变速箱" :value="car.automaticGear ? '自动挡' : '手动挡'" />
      <van-cell title="里程" :value="`${car.mileage || 0} km`" />
    </van-cell-group>

    <van-cell-group inset title="车辆配置" v-if="attrRows.length">
      <van-cell v-for="row in attrRows" :key="row.label" :title="row.label" :value="row.value" />
    </van-cell-group>

    <van-cell-group inset title="租车信息">
      <van-cell title="预计还车时间" center>
        <template #value>
          <input class="native-datetime" type="datetime-local" v-model="form.endTime" />
        </template>
      </van-cell>
      <van-field v-model="form.remark" label="备注" placeholder="选填" />
    </van-cell-group>

    <div class="submit-area">
      <van-button round block type="primary" :disabled="rentDisabled" :loading="submitting" @click="submit">
        {{ rentButtonText }}
      </van-button>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { showToast } from 'vant'
import uni from '@shared/utils/uni'
import { carApi, rentalApi, storeApi } from '@shared/api'
import { useAuthStore } from '@shared/store/auth'
import {
  carStatusText,
  carStatusTag,
  carTypeText,
  fuelTypeText,
  toBackendDateTime
} from '@shared/utils/dict'

const route = useRoute()
const auth = useAuthStore()

const car = ref(null)
const attributes = ref(null)
const store = ref(null)
const submitting = ref(false)

const pad = (n) => String(n).padStart(2, '0')
function defaultEndTime() {
  const d = new Date(Date.now() + 24 * 3600 * 1000)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}
const form = reactive({ endTime: defaultEndTime(), remark: '' })

const featureText = {
  fastCharge: '支持快充',
  reverseCamera: '倒车影像',
  radar: '倒车雷达',
  bluetooth: '蓝牙',
  airCondition: '空调',
  cruiseControl: '定速巡航',
  sunroof: '天窗',
  leatherSeat: '真皮座椅'
}

/**
 * 把属性整理成「标签 + 值」列表。
 * 注意不要只在布尔配置为 true 时才显示整块 —— 只要录了任何一项（比如续航）就应该展示。
 */
const attrRows = computed(() => {
  const a = attributes.value
  if (!a) return []
  const rows = []
  if (a.maxRange) rows.push({ label: '最大续航', value: `${a.maxRange} km` })
  if (a.batteryCapacity) rows.push({ label: '电池容量', value: `${a.batteryCapacity} kWh` })
  if (a.frontTrunkVolume) rows.push({ label: '前备箱容积', value: `${a.frontTrunkVolume} L` })
  if (a.trunkVolume) rows.push({ label: '后备箱容积', value: `${a.trunkVolume} L` })
  Object.keys(featureText).forEach((key) => {
    if (a[key]) rows.push({ label: featureText[key], value: '有' })
  })
  return rows
})

const canRent = computed(() => !!car.value && car.value.status === 'FREE')
const storeLabel = computed(() => {
  if (store.value) return store.value.name
  return car.value?.storeId ? '加载中…' : '未分配门店'
})
const rentDisabled = computed(() => submitting.value || (auth.isLogin && !canRent.value))
const rentButtonText = computed(() => {
  if (!auth.isLogin) return '登录后即可租车'
  if (!car.value) return '加载中'
  return car.value.status === 'FREE' ? '立即租用' : '该车辆当前不可租'
})

async function load() {
  const id = route.query.id
  try {
    car.value = await carApi.detail(id)
  } catch (e) {
    showToast(e.message || '车辆不存在')
    return
  }
  try {
    attributes.value = await carApi.attributes(id)
  } catch (e) {
    attributes.value = null
  }
  if (car.value.storeId) {
    try {
      store.value = await storeApi.detail(car.value.storeId)
    } catch (e) {
      store.value = null
    }
  }
}

async function submit() {
  if (!auth.isLogin) {
    uni.navigateTo('/pages/login/index')
    return
  }
  submitting.value = true
  try {
    const orderId = await rentalApi.create({
      carId: car.value.carId,
      endTime: toBackendDateTime(form.endTime),
      remark: form.remark
    })
    showToast({ type: 'success', message: '租车成功' })
    setTimeout(() => uni.redirectTo(`/pages/order/detail?id=${orderId}`), 700)
  } catch (e) {
    showToast(e.message || '下单失败')
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.detail-cover {
  margin: 12px;
  height: 160px;
  font-size: 56px;
}

.native-datetime {
  border: none;
  outline: none;
  text-align: right;
  font-size: 14px;
  color: #323233;
  background: transparent;
  width: 170px;
}

.submit-area {
  margin: 16px;
}
</style>
