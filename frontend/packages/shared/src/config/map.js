/**
 * 高德地图配置
 * Key 从 frontend/.env 读取（VITE_AMAP_KEY），不写死在代码里。
 */
export const AMAP_KEY = import.meta.env.VITE_AMAP_KEY || '';
export const AMAP_SECURITY_CODE = import.meta.env.VITE_AMAP_SECURITY_CODE || '';

/** 默认中心点：[经度, 纬度]，默认上海人民广场 */
export const DEFAULT_CENTER = [121.473701, 31.230416];

/** 「附近车辆」的判定半径（km） */
export const NEARBY_RADIUS_KM = 10;

export const hasAmapKey = () => !!AMAP_KEY;
