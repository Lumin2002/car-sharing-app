import uni from './uni'

const ACCESS_KEY = 'access_token'
const REFRESH_KEY = 'refresh_token'

export function getAccessToken() {
  return uni.getStorageSync(ACCESS_KEY) || ''
}

export function getRefreshToken() {
  return uni.getStorageSync(REFRESH_KEY) || ''
}

export function setTokens({ accessToken, refreshToken }) {
  if (accessToken) uni.setStorageSync(ACCESS_KEY, accessToken)
  if (refreshToken) uni.setStorageSync(REFRESH_KEY, refreshToken)
}

export function clearTokens() {
  uni.removeStorageSync(ACCESS_KEY)
  uni.removeStorageSync(REFRESH_KEY)
}
