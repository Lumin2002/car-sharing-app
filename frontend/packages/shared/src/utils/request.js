import uni from './uni'
import { getAccessToken, getRefreshToken, setTokens, clearTokens } from './token'

const BASE_URL = ''

let refreshing = null

// 这些接口的 401 属于业务错误（账号密码错误等），不能当成 token 过期去续期
const AUTH_APIS = ['/api/auth/login', '/api/auth/register', '/api/auth/refresh']
const isAuthApi = (url) => AUTH_APIS.some((api) => url.startsWith(api))

function rawRequest({ url, method = 'GET', data, header = {} }) {
  const token = getAccessToken()
  const finalHeader = { ...header }
  if (token) finalHeader.Authorization = 'Bearer ' + token
  return uni.request({ url: BASE_URL + url, method, data, header: finalHeader })
}

/** 用 refresh token 换新的 access token（并发时只发一次请求） */
function refreshToken() {
  if (!refreshing) {
    const refresh = getRefreshToken()
    if (!refresh) {
      return Promise.reject(new Error('no refresh token'))
    }
    refreshing = uni
      .request({
        url: BASE_URL + '/api/auth/refresh',
        method: 'POST',
        header: { 'Refresh-Token': refresh }
      })
      .then((res) => {
        const body = res.data
        if (res.statusCode === 200 && body && body.code === 200) {
          setTokens(body.data)
          return true
        }
        throw new Error('refresh failed')
      })
      .finally(() => {
        refreshing = null
      })
  }
  return refreshing
}

function toLogin() {
  clearTokens()
  uni.reLaunch('/pages/login/index')
}

/**
 * 统一请求封装：
 * - 后端成功时返回 { code:200, data }，这里直接把 data 返回出去
 * - 非登录类接口返回 401 时，自动用 refresh token 续期并重试一次
 * - 其它错误抛出 { code, message }
 */
export async function request(options, retried = false) {
  let res
  try {
    res = await rawRequest(options)
  } catch (e) {
    throw { code: -1, message: '网络异常，请确认后端服务已启动' }
  }

  const { statusCode, data: body } = res
  const isResultJson = body !== null && typeof body === 'object' && 'code' in body

  if (statusCode !== 200) {
    console.error('[request]', (options.method || 'GET'), options.url, '->', statusCode, body)
  }

  if (statusCode === 200 && isResultJson && body.code === 200) {
    return body.data
  }

  // 登录 / 注册 / 刷新 自己的 401 就是业务提示，直接透传给页面
  if (statusCode === 401 && !isAuthApi(options.url) && !retried) {
    try {
      await refreshToken()
      return request(options, true)
    } catch (e) {
      toLogin()
      throw { code: 401, message: '登录已失效，请重新登录' }
    }
  }

  // 后端返回的是标准 ResultVO 时，把它的 message 原样抛出
  if (isResultJson) {
    throw body
  }

  // 否则说明请求没到后端（比如后端没启动、代理失败），给出可排查的提示
  throw {
    code: statusCode,
    message: `服务无响应（HTTP ${statusCode}），请确认后端已启动`
  }
}

export default request
