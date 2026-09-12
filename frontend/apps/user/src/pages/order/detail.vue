<template>
  <div class="page" v-if="order">
    <van-cell-group inset title="订单信息">
      <van-cell title="订单号" :value="order.orderNo" />
      <van-cell title="状态">
        <template #value>
          <van-tag :type="rentalStatusTag[order.status]">
            {{ rentalStatusText[order.status] }}
          </van-tag>
        </template>
      </van-cell>
      <van-cell title="车辆" :value="carLabel" />
    </van-cell-group>

    <van-cell-group inset title="租期">
      <van-cell title="开始时间" :value="formatDateTime(order.startTime)" />
      <van-cell title="预计还车" :value="formatDateTime(order.endTime)" />
      <van-cell v-if="order.actualReturnTime" title="实际还车" :value="formatDateTime(order.actualReturnTime)" />
      <van-cell title="租用天数" :value="`${order.rentDays} 天`" />
    </van-cell-group>

    <van-cell-group inset title="费用">
      <van-cell title="日租金" :value="`¥${order.dailyPrice}`" />
      <van-cell title="租金" :value="`¥${order.rentAmount}`" />
      <van-cell title="押金" :value="`¥${order.deposit || 0}`" />
      <van-cell title="合计">
        <template #value><span class="price">¥{{ order.totalAmount }}</span></template>
      </van-cell>
    </van-cell-group>

    <van-cell-group inset title="支付记录" v-if="payments.length">
      <van-cell v-for="p in payments" :key="p.id" :title="payTypeText[p.payType] || p.payType">
        <template #value>
          <span>¥{{ p.amount }}</span>
          <van-tag class="ml6" :type="p.status === 'REFUNDED' ? 'warning' : 'success'">
            {{ paymentStatusText[p.status] || p.status }}
          </van-tag>
        </template>
      </van-cell>
    </van-cell-group>

    <van-cell-group inset title="还车结算" v-if="settlement">
      <van-cell title="状态">
        <template #value>
          <van-tag :type="settlementStatusTag[settlement.status]">
            {{ settlementStatusText[settlement.status] || settlement.status }}
          </van-tag>
        </template>
      </van-cell>
      <van-cell title="超里程" :value="`${settlement.exceedMileage || 0} km`" />
      <van-cell title="超里程费" :value="`¥${settlement.exceedMileageFee || 0}`" />
      <van-cell title="超时" :value="`${settlement.overtimeMinute || 0} 分钟`" />
      <van-cell title="超时费" :value="`¥${settlement.overtimeFee || 0}`" />
      <van-cell title="押金扣罚" :value="`¥${settlement.depositDeductAmount || 0}`" />
      <van-cell title="押金退还">
        <template #value>
          <span class="price">¥{{ settlement.depositRefundAmount || 0 }}</span>
        </template>
      </van-cell>
      <van-cell v-if="settlement.remark" title="说明" :value="settlement.remark" />
    </van-cell-group>

    <van-cell-group inset title="退款记录" v-if="refunds.length">
      <van-cell v-for="r in refunds" :key="r.id" :title="refundTypeText[r.refundType] || r.refundType">
        <template #value>
          <span class="price">¥{{ r.amount }}</span>
        </template>
      </van-cell>
    </van-cell-group>

    <van-cell-group inset title="里程" v-if="order.mileageBefore != null || order.mileageAfter != null">
      <van-cell title="取车里程" :value="`${order.mileageBefore ?? '-'} km`" />
      <van-cell title="还车里程" :value="`${order.mileageAfter ?? '-'} km`" />
    </van-cell-group>

    <van-cell-group inset title="备注" v-if="order.remark || order.cancelReason">
      <van-cell v-if="order.remark" title="备注" :value="order.remark" />
      <van-cell v-if="order.cancelReason" title="取消原因" :value="order.cancelReason" />
    </van-cell-group>

    <div class="actions" v-if="isActive">
      <van-button block plain @click="cancel">取消订单</van-button>
      <van-button v-if="!paid" block type="primary" @click="payVisible = true">去支付</van-button>
      <van-button v-else block type="primary" @click="returnVisible = true">立即还车</van-button>
    </div>

    <van-action-sheet
      v-model:show="payVisible"
      :actions="payMethods"
      cancel-text="取消"
      description="选择支付方式（当前为模拟支付，点击即成功）"
      @select="doPay"
    />

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
import { useRoute } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import uni from '@shared/utils/uni'
import { carApi, paymentApi, refundApi, rentalApi, settlementApi } from '@shared/api'
import {
  rentalStatusText,
  rentalStatusTag,
  payTypeText,
  paymentStatusText,
  refundTypeText,
  settlementStatusText,
  settlementStatusTag,
  formatDateTime
} from '@shared/utils/dict'

const route = useRoute()
const order = ref(null)
const car = ref(null)
const orderId = route.query.id

const returnVisible = ref(false)
const mileageAfter = ref('')
const payVisible = ref(false)

const payments = ref([])
const settlement = ref(null)
const refunds = ref([])

const payMethods = [
  { name: '支付宝', value: 'ALIPAY' },
  { name: '微信支付', value: 'WECHAT' },
  { name: '银行卡', value: 'BANK' }
]

const isActive = computed(
  () => order.value?.status === 'RENTING' || order.value?.status === 'OVERDUE'
)
const paid = computed(() =>
  payments.value.some((p) => p.payType === 'RENT_PAY' && p.status !== 'REFUNDED')
)

const carLabel = computed(() => {
  if (!car.value) return `车辆 #${order.value?.carId ?? ''}`
  return `${car.value.brand} ${car.value.model || ''} · ${car.value.plateNo}`
})

async function load() {
  try {
    order.value = await rentalApi.detail(orderId)
  } catch (e) {
    showToast(e.message || '订单不存在')
    return
  }
  try {
    car.value = await carApi.detail(order.value.carId)
  } catch (e) {
    car.value = null
  }
  await loadFinance()
}

async function loadFinance() {
  try {
    payments.value = (await paymentApi.byOrder(orderId)) || []
  } catch (e) {
    payments.value = []
  }
  try {
    settlement.value = await settlementApi.byOrder(orderId)
  } catch (e) {
    settlement.value = null
  }
  try {
    refunds.value = (await refundApi.byOrder(orderId)) || []
  } catch (e) {
    refunds.value = []
  }
}

async function doPay(action) {
  payVisible.value = false
  try {
    await paymentApi.pay({ orderId: Number(orderId), payMethod: action.value })
    showToast({ type: 'success', message: '支付成功' })
    load()
  } catch (e) {
    showToast(e.message || '支付失败')
  }
}

async function doReturn() {
  const payload = mileageAfter.value === '' ? {} : { mileageAfter: Number(mileageAfter.value) }
  try {
    await rentalApi.returnCar(orderId, payload)
    showToast({ type: 'success', message: '还车成功' })
    load()
  } catch (e) {
    showToast(e.message || '还车失败')
  }
}

function cancel() {
  showConfirmDialog({
    title: '取消订单',
    message: '确定要取消这笔租赁订单吗？'
  })
    .then(() => rentalApi.cancel(orderId, '用户取消'))
    .then(() => {
      showToast({ type: 'success', message: '已取消' })
      load()
    })
    .catch((e) => {
      if (e && e.message && e.message !== 'cancel') showToast(e.message)
    })
}

onMounted(load)
</script>

<style scoped>
.actions {
  display: flex;
  gap: 12px;
  margin: 16px;
}

.ml6 {
  margin-left: 6px;
}
</style>
