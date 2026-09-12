<template>
  <div class="page">
    <van-tabs v-model:active="activeIndex" @change="onTabChange">
      <van-tab v-for="t in tabs" :key="t.value" :title="t.label" />
    </van-tabs>

    <van-empty v-if="!auth.isLogin" description="登录后查看订单">
      <van-button size="small" type="primary" @click="uni.navigateTo('/pages/login/index')">
        去登录
      </van-button>
    </van-empty>

    <template v-else>
      <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
        <van-list
          v-model:loading="loading"
          :finished="finished"
          finished-text="没有更多了"
          @load="onLoad"
        >
          <van-empty v-if="!loading && orders.length === 0" description="还没有订单，去租一辆车吧" />

          <div
            v-for="(order, index) in orders"
            :key="order?.orderId ?? index"
            class="card-block"
            @click="toDetail(order?.orderId)"
          >
            <div class="row-between">
              <div class="order-no">{{ order?.orderNo }}</div>
              <van-tag :type="rentalStatusTag[order?.status]">
                {{ rentalStatusText[order?.status] }}
              </van-tag>
            </div>
            <div class="muted mt6">{{ carLabel(order?.carId) }}</div>
            <div class="muted mt4">
              {{ formatDateTime(order?.startTime) }} ~ {{ formatDateTime(order?.endTime) }}
            </div>
            <div class="row-between mt10">
              <div>
                <span class="price">¥{{ order?.totalAmount ?? 0 }}</span>
                <span class="muted"> 含押金 ¥{{ order?.deposit || 0 }}</span>
              </div>
            </div>
            <div class="actions" v-if="isActive(order)">
              <van-button size="small" plain @click.stop="cancel(order)">取消</van-button>
              <van-button
                v-if="!isPaid(order)"
                size="small"
                type="primary"
                @click.stop="toDetail(order.orderId)"
              >
                去支付
              </van-button>
              <van-button v-else size="small" type="primary" @click.stop="openReturn(order)">
                还车
              </van-button>
            </div>
          </div>
        </van-list>
      </van-pull-refresh>
    </template>

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
import { showToast, showConfirmDialog } from 'vant'
import uni from '@shared/utils/uni'
import { carApi, paymentApi, rentalApi } from '@shared/api'
import { useAuthStore } from '@shared/store/auth'
import {
  rentalStatusText,
  rentalStatusTag,
  formatDateTime
} from '@shared/utils/dict'

const auth = useAuthStore()
const tabs = [
  { label: '全部', value: '' },
  { label: '租用中', value: 'RENTING' },
  { label: '已逾期', value: 'OVERDUE' },
  { label: '已还车', value: 'RETURNED' },
  { label: '已取消', value: 'CANCELLED' }
]

const activeIndex = ref(0)
const status = computed(() => (tabs[activeIndex.value] || tabs[0]).value)

const orders = ref([])
const carMap = ref({})
const pageNum = ref(1)
const pageSize = 10
const loading = ref(false)
const finished = ref(false)
const refreshing = ref(false)

const returnVisible = ref(false)
const mileageAfter = ref('')
const returnTarget = ref(null)
/** orderId -> 是否已支付租金 */
const paidMap = ref({})

const isActive = (order) => order?.status === 'RENTING' || order?.status === 'OVERDUE'
const isPaid = (order) => !!paidMap.value[order?.orderId]

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
        // 车辆可能已被删除
      }
    })
  )
}

/**
 * 只查「进行中」订单的支付状态。
 * 后端限制了一个用户同时只能有一笔未完成订单，所以这里最多多花一次请求。
 */
async function loadPaidState(list) {
  const actives = list.filter(isActive).filter((o) => paidMap.value[o.orderId] === undefined)
  await Promise.all(
    actives.map(async (order) => {
      try {
        const payments = (await paymentApi.byOrder(order.orderId)) || []
        paidMap.value[order.orderId] = payments.some(
          (p) => p.payType === 'RENT_PAY' && p.status !== 'REFUNDED'
        )
      } catch (e) {
        paidMap.value[order.orderId] = false
      }
    })
  )
}

async function onLoad() {
  if (!auth.isLogin || finished.value) {
    loading.value = false
    return
  }
  try {
    const data = await rentalApi.my({
      pageNum: pageNum.value,
      pageSize,
      status: status.value
    })
    const records = data.records || []
    orders.value = orders.value.concat(records)
    pageNum.value += 1
    finished.value = records.length < pageSize || orders.value.length >= (data.total || 0)
    await loadCars(orders.value)
    await loadPaidState(orders.value)
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
  orders.value = []
  finished.value = false
  loading.value = true
  await onLoad()
}

async function onRefresh() {
  await reload()
}

function onTabChange() {
  reload()
}

function toDetail(id) {
  uni.navigateTo(`/pages/order/detail?id=${id}`)
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

function cancel(order) {
  showConfirmDialog({
    title: '取消订单',
    message: '确定要取消这笔租赁订单吗？'
  })
    .then(() => rentalApi.cancel(order.orderId, '用户取消'))
    .then(() => {
      showToast({ type: 'success', message: '已取消' })
      reload()
    })
    .catch((e) => {
      // 用户点了「取消」按钮也会走到这里
      if (e && e.message && e.message !== 'cancel') showToast(e.message)
    })
}

onMounted(() => {
  if (auth.isLogin) reload()
})
</script>

<style scoped>
.row-between {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.order-no {
  font-size: 14px;
  font-weight: 600;
}

.mt4 {
  margin-top: 4px;
}

.mt6 {
  margin-top: 6px;
}

.mt10 {
  margin-top: 10px;
}

.actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  margin-top: 10px;
}
</style>
