<template>
  <div class="map-wrap">
    <div ref="container" class="map-box"></div>

    <div v-if="!hasKey" class="map-mask">
      <div class="mask-title">未配置高德地图 Key</div>
      <div class="mask-tip">
        打开 <code>.env</code>，把 <code>VITE_AMAP_KEY</code> 填成你的高德「Web端(JS API)」Key，然后重启
        <code>npm run dev</code>
      </div>
    </div>

    <div v-else-if="error" class="map-mask">
      <div class="mask-title">地图加载失败</div>
      <div class="mask-tip">{{ error }}</div>
    </div>

    <div v-else-if="!locatedPoints.length" class="map-hint">暂无门店</div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { AMAP_KEY, AMAP_SECURITY_CODE, DEFAULT_CENTER, hasAmapKey } from '../config/map'

/**
 * 通用地图组件：按传入的点位画标记
 * markers: [{ id, title, longitude, latitude }]
 */
const props = defineProps({
  markers: { type: Array, default: () => [] },
  /** 定位到的中心点 [lng, lat]，为空则用默认中心 */
  center: { type: Array, default: null },
  /** 附近范围半径（km），大于 0 时画范围圈 */
  radiusKm: { type: Number, default: 0 }
})

const emit = defineEmits(['marker-click'])

const container = ref(null)
const error = ref('')
const hasKey = computed(() => hasAmapKey())

let map = null
let markers = []
let meMarker = null
let circle = null
let AMapRef = null

const locatedPoints = computed(() =>
  props.markers.filter((m) => m.longitude != null && m.latitude != null)
)

function loadAmapScript() {
  return new Promise((resolve, reject) => {
    if (window.AMap) return resolve(window.AMap)
    if (AMAP_SECURITY_CODE) {
      window._AMapSecurityConfig = { securityJsCode: AMAP_SECURITY_CODE }
    }
    const script = document.createElement('script')
    script.src = `https://webapi.amap.com/maps?v=2.0&key=${AMAP_KEY}`
    script.onload = () =>
      window.AMap ? resolve(window.AMap) : reject(new Error('高德脚本已加载但未初始化'))
    script.onerror = () => reject(new Error('高德脚本加载失败，请检查网络或 Key 是否正确'))
    document.head.appendChild(script)
  })
}

function clearMarkers() {
  if (markers.length && map) map.remove(markers)
  markers = []
  if (meMarker && map) map.remove(meMarker)
  if (circle && map) map.remove(circle)
  meMarker = null
  circle = null
}

function render() {
  if (!map || !AMapRef) return
  clearMarkers()

  const center = props.center

  locatedPoints.value.forEach((point) => {
    const marker = new AMapRef.Marker({
      position: [Number(point.longitude), Number(point.latitude)],
      title: point.title || ''
    })
    marker.on('click', () => emit('marker-click', point.id))
    marker.setMap(map)
    markers.push(marker)
  })

  // 有定位：显示「我的位置」蓝点 + 附近范围圈，并把地图居中到当前位置
  if (center) {
    meMarker = new AMapRef.Marker({
      position: center,
      offset: new AMapRef.Pixel(-7, -7),
      content: '<div class="me-dot"></div>',
      zIndex: 200
    })
    meMarker.setMap(map)

    if (props.radiusKm > 0) {
      circle = new AMapRef.Circle({
        center,
        radius: props.radiusKm * 1000,
        strokeColor: '#1989fa',
        strokeOpacity: 0.5,
        strokeWeight: 1,
        fillColor: '#1989fa',
        fillOpacity: 0.08
      })
      circle.setMap(map)
    }

    map.setCenter(center)
    map.setZoom(props.radiusKm > 0 ? 11 : 13)
  } else if (markers.length) {
    map.setFitView(markers, false, [60, 60, 60, 60], 14)
  }
}

onMounted(async () => {
  if (!hasKey.value) return
  try {
    AMapRef = await loadAmapScript()
    map = new AMapRef.Map(container.value, {
      zoom: 11,
      center: DEFAULT_CENTER,
      viewMode: '2D'
    })
    render()
  } catch (e) {
    error.value = e.message || '地图初始化失败'
  }
})

watch(
  () => [props.markers, props.center, props.radiusKm],
  () => render(),
  { deep: true }
)

onBeforeUnmount(() => {
  clearMarkers()
  if (map) {
    map.destroy()
    map = null
  }
})
</script>

<style scoped>
.map-wrap {
  position: relative;
  height: 240px;
  border-radius: 12px;
  overflow: hidden;
  background: #e9edf5;
  box-shadow: 0 1px 4px rgba(20, 30, 60, 0.05);
  margin: 12px;
}

.map-box {
  width: 100%;
  height: 100%;
}

.map-mask {
  position: absolute;
  inset: 0;
  background: rgba(245, 246, 248, 0.94);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 16px;
}

.mask-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 6px;
}

.mask-tip {
  font-size: 12px;
  color: #8a8f99;
  line-height: 1.6;
}

.mask-tip code {
  background: #eceef2;
  border-radius: 4px;
  padding: 1px 4px;
}

.map-hint {
  position: absolute;
  left: 10px;
  bottom: 10px;
  background: rgba(0, 0, 0, 0.6);
  color: #fff;
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 12px;
}
</style>
