/** 地球半径(km) */
const EARTH_RADIUS = 6371

const toRad = (deg) => (deg * Math.PI) / 180

/** 两点间距离（km），Haversine 公式 */
export function distanceKm(lng1, lat1, lng2, lat2) {
  const dLat = toRad(lat2 - lat1)
  const dLng = toRad(lng2 - lng1)
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2
  return 2 * EARTH_RADIUS * Math.asin(Math.sqrt(a))
}

/** 距离展示：小于 1km 显示米 */
export function formatDistance(km) {
  if (km == null) return ''
  return km < 1 ? `${Math.round(km * 1000)}m` : `${km.toFixed(1)}km`
}

/**
 * 获取浏览器定位（需要 localhost 或 HTTPS）。
 * 返回 { longitude, latitude }
 */
export function getCurrentPosition(options = {}) {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) {
      reject(new Error('当前浏览器不支持定位'))
      return
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => resolve({ longitude: pos.coords.longitude, latitude: pos.coords.latitude }),
      (err) => {
        const msg =
          err && err.code === 1
            ? '定位权限被拒绝'
            : err && err.code === 3
              ? '定位超时'
              : '定位失败'
        reject(new Error(msg))
      },
      { enableHighAccuracy: false, timeout: 8000, maximumAge: 60_000, ...options }
    )
  })
}
