export const carStatusText = {
  FREE: '空闲',
  RENTED: '已租出',
  BOOKED: '已预订',
  MAINTENANCE: '维护中',
  DISABLED: '已停用'
}

export const rentalStatusText = {
  RENTING: '租用中',
  OVERDUE: '已逾期',
  RETURNED: '已还车',
  CANCELLED: '已取消'
}

/** 标签类型映射（对应 Vant 的 van-tag type） */
export const carStatusTag = {
  FREE: 'success',
  RENTED: 'danger',
  BOOKED: 'primary',
  MAINTENANCE: 'warning',
  DISABLED: 'default'
}

export const rentalStatusTag = {
  RENTING: 'primary',
  OVERDUE: 'danger',
  RETURNED: 'default',
  CANCELLED: 'danger'
}

/* ------------------------------ 认证 ------------------------------ */

export const authStatusText = {
  PENDING: '待审核',
  APPROVED: '已通过',
  REJECTED: '已驳回'
}

export const authStatusTag = {
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger'
}

/* ------------------------------ 资金 ------------------------------ */

export const payTypeText = {
  RENT_PAY: '租金支付',
  DEPOSIT_FROZEN: '押金冻结'
}

export const payMethodText = {
  ALIPAY: '支付宝',
  WECHAT: '微信支付',
  BANK: '银行卡',
  CASH: '现金',
  BALANCE: '账户余额'
}

export const paymentStatusText = {
  INIT: '待处理',
  SUCCESS: '成功',
  FAIL: '失败',
  REFUNDED: '已退款'
}

export const refundTypeText = {
  RENT_REFUND: '租金退款',
  DEPOSIT_UNFREEZE: '押金解冻',
  DEPOSIT_DEDUCT: '押金扣罚'
}

export const settlementStatusText = {
  PENDING: '待确认',
  CONFIRMED: '已确认',
  FINISHED: '已结算'
}

export const settlementStatusTag = {
  PENDING: 'warning',
  CONFIRMED: 'primary',
  FINISHED: 'success'
}

/* ------------------------------ 站内消息 ------------------------------ */

export const messageTypeText = {
  101: '系统公告',
  102: '系统告警',
  201: '订单创建成功',
  202: '订单支付成功',
  203: '租车已开始',
  204: '订单即将到期',
  205: '订单逾期提醒',
  206: '订单结算完成',
  207: '订单已取消',
  301: '账户充值成功',
  302: '押金冻结通知',
  303: '押金解冻通知',
  304: '费用扣除通知',
  305: '退款到账通知',
  401: '车辆维护通知'
}

/** 消息类型 -> 图标，列表里更直观 */
export const messageTypeIcon = {
  101: 'volume-o',
  102: 'warning-o',
  201: 'add-o',
  202: 'paid',
  203: 'logistics',
  204: 'clock-o',
  205: 'warning-o',
  206: 'passed',
  207: 'close',
  301: 'gold-coin-o',
  302: 'lock',
  303: 'lock',
  304: 'minus',
  305: 'refund-o',
  401: 'setting-o'
}

export const fuelTypeText = {
  GASOLINE: '汽油',
  DIESEL: '柴油',
  ELECTRIC: '新能源',
  HYBRID: '混动'
}

export const carTypeText = {
  ECONOMY: '经济型',
  COMFORT: '舒适型',
  PREMIUM: '高端型'
}

export const roleText = {
  USER: '普通用户',
  SUPPLIER: '供应商',
  OPERATIONS: '运维人员',
  ADMIN: '管理员'
}

export const userStatusText = {
  ENABLED: '正常',
  DISABLED: '已禁用'
}

export const storeStatusText = {
  OPEN: '营业中',
  CLOSED: '已打烊'
}

/** 把 "2026-09-11T10:00" 补成后端需要的 "2026-09-11T10:00:00" */
export function toBackendDateTime(value) {
  if (!value) return value
  return value.length === 16 ? value + ':00' : value
}

/** 简单的时间展示：2026-09-11T10:00:00 -> 2026-09-11 10:00 */
export function formatDateTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}
