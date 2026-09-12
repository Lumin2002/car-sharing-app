import { createRouter, createWebHashHistory } from 'vue-router'
import pagesJson from '../pages.json'

// 以 pages.json 作为路由唯一来源，贴近 uni-app 的组织方式
const modules = import.meta.glob('../pages/**/*.vue')

const routes = pagesJson.pages.map((page) => ({
  path: '/' + page.path,
  name: page.path,
  component: modules[`../${page.path}.vue`],
  meta: {
    title: page.style && page.style.navigationBarTitleText,
    isTab: (pagesJson.tabBar?.list || []).some((t) => t.pagePath === page.path)
  }
}))

routes.push({ path: '/', redirect: '/pages/home/index' })
routes.push({ path: '/:pathMatch(.*)*', redirect: '/pages/home/index' })

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

export default router
