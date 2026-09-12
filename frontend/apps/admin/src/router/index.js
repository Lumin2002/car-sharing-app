import { createRouter, createWebHashHistory } from 'vue-router'
import pagesJson from '../pages.json'

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

routes.push({ path: '/', redirect: '/pages/dashboard/index' })
routes.push({ path: '/:pathMatch(.*)*', redirect: '/pages/dashboard/index' })

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

export default router
