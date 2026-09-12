import request from '../utils/request'
import { getRefreshToken } from '../utils/token'

function qs(params = {}) {
  const query = new URLSearchParams(
    Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== '')
  ).toString()
  return query ? '?' + query : ''
}

export const authApi = {
  register: (data) => request({ url: '/api/auth/register', method: 'POST', data }),
  login: (data) => request({ url: '/api/auth/login', method: 'POST', data }),
  current: () => request({ url: '/api/auth/current' }),
  // 后端要求带上自己的 refreshToken，否则返回 400；空值时不拼参数，让 store 直接清本地状态
  logout: () => {
    const refreshToken = getRefreshToken()
    const q = refreshToken ? '?refreshToken=' + encodeURIComponent(refreshToken) : ''
    return request({ url: '/api/auth/logout' + q, method: 'POST' })
  },
  changePassword: (data) => request({ url: '/api/user/changePassword', method: 'PUT', data })
}

export const carApi = {
  page: (params) => request({ url: '/api/car/page' + qs(params) }),
  detail: (id) => request({ url: `/api/car/${id}` }),
  attributes: (id) => request({ url: `/api/car/${id}/attributes` }),
  saveAttributes: (id, data) => request({ url: `/api/car/${id}/attributes`, method: 'PUT', data }),
  add: (data) => request({ url: '/api/car', method: 'POST', data }),
  update: (id, data) => request({ url: `/api/car/${id}`, method: 'PUT', data }),
  remove: (id) => request({ url: `/api/car/${id}`, method: 'DELETE' }),
  changeStatus: (id, status) => request({ url: `/api/car/${id}/status?status=${status}`, method: 'PUT' })
}

/** 门店（持有经纬度，车辆归属门店） */
export const storeApi = {
  list: (params) => request({ url: '/api/store/list' + qs(params) }),
  page: (params) => request({ url: '/api/store/page' + qs(params) }),
  detail: (id) => request({ url: `/api/store/${id}` }),
  rentableCars: (id) => request({ url: `/api/store/${id}/cars` }),
  add: (data) => request({ url: '/api/store', method: 'POST', data }),
  update: (id, data) => request({ url: `/api/store/${id}`, method: 'PUT', data }),
  remove: (id) => request({ url: `/api/store/${id}`, method: 'DELETE' })
}

export const rentalApi = {
  create: (data) => request({ url: '/api/rental', method: 'POST', data }),
  my: (params) => request({ url: '/api/rental/my' + qs(params) }),
  page: (params) => request({ url: '/api/rental/page' + qs(params) }),
  detail: (id) => request({ url: `/api/rental/${id}` }),
  returnCar: (id, data) => request({ url: `/api/rental/${id}/return`, method: 'PUT', data }),
  cancel: (id, reason) => request({ url: `/api/rental/${id}/cancel?reason=${encodeURIComponent(reason || '')}`, method: 'PUT' })
}

/** 支付：下单后支付租金 + 冻结押金 */
export const paymentApi = {
  pay: (data) => request({ url: '/api/payment/pay', method: 'POST', data }),
  my: (params) => request({ url: '/api/payment/my' + qs(params) }),
  byOrder: (orderId) => request({ url: `/api/payment/order/${orderId}` })
}

/** 退款 / 押金解冻记录 */
export const refundApi = {
  my: (params) => request({ url: '/api/refund/my' + qs(params) }),
  byOrder: (orderId) => request({ url: `/api/refund/order/${orderId}` })
}

/** 还车结算单 */
export const settlementApi = {
  my: (params) => request({ url: '/api/settlement/my' + qs(params) }),
  byOrder: (orderId) => request({ url: `/api/settlement/order/${orderId}` }),
  confirm: (id) => request({ url: `/api/settlement/${id}/confirm`, method: 'POST' })
}

/** 站内消息 */
export const messageApi = {
  my: (params) => request({ url: '/api/message/my' + qs(params) }),
  unreadCount: () => request({ url: '/api/message/unread/count' }),
  read: (id) => request({ url: `/api/message/${id}/read`, method: 'PUT' }),
  readAll: () => request({ url: '/api/message/read-all', method: 'PUT' }),
  remove: (id) => request({ url: `/api/message/${id}`, method: 'DELETE' })
}

/** 实名 / 驾照认证 */
export const verificationApi = {
  me: () => request({ url: '/api/verification/me' }),

  submitRealname: (data) => request({ url: '/api/verification/realname', method: 'POST', data }),
  myRealname: () => request({ url: '/api/verification/realname' }),
  realnamePage: (params) => request({ url: '/api/verification/realname/page' + qs(params) }),
  auditRealname: (id, approved, reason) =>
    request({
      url:
        `/api/verification/realname/${id}/audit?approved=${approved}` +
        `&reason=${encodeURIComponent(reason || '')}`,
      method: 'PUT'
    }),

  submitLicense: (data) => request({ url: '/api/verification/license', method: 'POST', data }),
  myLicense: () => request({ url: '/api/verification/license' }),
  licensePage: (params) => request({ url: '/api/verification/license/page' + qs(params) }),
  auditLicense: (id, approved, reason) =>
    request({
      url:
        `/api/verification/license/${id}/audit?approved=${approved}` +
        `&reason=${encodeURIComponent(reason || '')}`,
      method: 'PUT'
    })
}

/** 操作日志（运维端） */
export const logApi = {
  page: (params) => request({ url: '/api/log/page' + qs(params) })
}

/** 文件上传：返回 { url, originalName, size, contentType }，url 直接填进业务字段 */
export const fileApi = {
  upload: (file, biz = 'other') => {
    const form = new FormData()
    form.append('file', file)
    form.append('biz', biz)
    // FormData 交给 uni.request，由它识别后不设置 Content-Type（浏览器要自己带 boundary）
    return request({ url: '/api/file/upload', method: 'POST', data: form })
  }
}

/** 用户管理（运维端，需要 ADMIN 权限） */
export const userApi = {
  page: (params) => request({ url: '/api/user/page' + qs(params) }),
  detail: (id) => request({ url: `/api/user/${id}` }),
  add: (data) => request({ url: '/api/user/add', method: 'POST', data }),
  update: (id, data) => request({ url: `/api/user/${id}`, method: 'PUT', data }),
  ban: (id, status) => request({ url: `/api/user/ban/${id}?status=${status}`, method: 'PUT' })
}
