# 共享汽车前端

Vue 3 + Vite + Vue Router + Pinia，采用 **npm workspaces 单仓多应用**：一个公共层 + 两个前端。

```
frontend/
  package.json               # workspace 根，统一脚本
  packages/shared/           # 两端共用的公共层
    src/
      utils/                 # uni 兼容层、请求封装、token、字典、地理计算
      api/                   # 所有后端接口定义（auth / car / rental / user）
      store/                 # Pinia 登录态
      components/            # NavBar、TabBar、EmptyState、MapView
      config/                # 地图配置
      styles/                # 全局样式
  apps/
    user/                    # 用户端（端口 5173）
    admin/                   # 运维管理端（端口 5174）
```

## 运行

先确保后端已启动（`mvn spring-boot:run`，端口 8080），然后：

```bash
cd frontend
npm install

npm run dev:user     # 用户端   http://localhost:5173
npm run dev:admin    # 运维端   http://localhost:5174
```

打包：

```bash
npm run build:user
npm run build:admin
npm run build        # 两个一起打
```

开发时两个应用都通过 Vite 代理把 `/api` 转发到 `http://127.0.0.1:8080`，因此**不需要改后端 CORS**。

## 两个端分别做什么

| | 用户端 `apps/user` | 运维管理端 `apps/admin` |
|---|---|---|
| 面向 | 租车用户 | 管理员 / 运维人员 |
| 形态 | **移动端 H5**（最大宽 480px 居中） | **移动端 H5**（同样 480px 居中） |
| UI 组件库 | **Vant 4** | **Vant 4**（两个端统一） |
| 登录 | 手机号注册 + 登录 | 仅 ADMIN / OPERATIONS 角色可登录 |
| 底部标签 | 找车 / 订单 / 我的 | 工作台 / 车辆 / 店铺 / 订单 / 我的 |
| 页面 | 首页（**门店地图 + 可租车辆列表**）、车辆详情下单、我的订单、订单详情、我的、修改密码 | 工作台（运营统计）、车辆管理、**店铺管理**、订单管理、用户管理、我的（含改密码） |
| 是否用地图 | 是（高德，地图上标记**门店**；按门店距离筛选） | 否 |

> 位置数据的归属：**经纬度只存在门店（`store` 表）上**，车辆通过 `store_id` 归属门店，车辆本身不再存坐标。
> 用户端首页只展示**可租用（FREE）**的车辆，并按「车辆所属门店」到用户的距离排序与筛选。

两个端都是给手机浏览器用的 H5：窄屏居中布局、底部标签栏导航、卡片式列表、表单控件按触摸尺寸设计。

UI 库都是**全量引入**（`app.use(Vant)`），好处是零配置、稳定；如果在意包体积，可以改成 `unplugin-vue-components` 按需引入。

共享层里只保留与 UI 库无关的东西（请求、工具、接口、登录态、地图组件）；导航栏 / 标签栏这类和 UI 库强相关的组件，由两个应用各自实现（都用 `van-nav-bar` + `van-tabbar`，只是标签项不同）。

## 共享层怎么用

两个应用都配置了两个别名（见各自的 `vite.config.js`）：

- `@` → 应用自己的 `src`
- `@shared` → `packages/shared/src`

所以页面里这样写：

```js
import uni from '@shared/utils/uni'
import { carApi } from '@shared/api'
import { useAuthStore } from '@shared/store/auth'
import MapView from '@shared/components/MapView.vue'
```

改公共逻辑（请求封装、token 续期、字典等）只需要改 `packages/shared` 一处，两端同时生效。

## 高德地图 Key（仅用户端需要）

1. 到 https://console.amap.com/dev/key/app 新建 Key，**服务类型选「Web端(JS API)」**；
2. 编辑 `apps/user/.env`：

```
VITE_AMAP_KEY=你的Key
VITE_AMAP_SECURITY_CODE=   # 若 Key 启用了安全密钥才需要填
```

3. 重启 `npm run dev:user`。

没配置 Key 时地图区域会显示配置提示，其余功能不受影响。车辆要出现在地图上，需要填 `longitude` / `latitude`。

## 与 uni-app 的对应关系

| 这里 | uni-app |
|---|---|
| `pages.json`（每个应用各一份） | `pages.json` |
| `@shared/utils/uni.js`（自研 shim） | 内置的 `uni` 对象 |
| `router/index.js` | 无需，uni-app 由 pages.json 自动注册 |
| `src/pages/xxx/xxx.vue` | `pages/xxx/xxx.vue` |

迁移到 uni-app 时，把 `@shared/utils/uni` 换成内置 `uni`、删掉 `router/`，页面代码基本可以原样拷贝。
