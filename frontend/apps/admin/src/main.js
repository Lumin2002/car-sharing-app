import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Vant from 'vant'
import 'vant/lib/index.css'
import App from './App.vue'
import router from './router'
import { registerRouter } from '@shared/utils/uni'
import '@shared/styles/global.css'
import './styles/admin.css'

registerRouter(router)

const app = createApp(App)

// 把出错的路由和组件打到控制台，方便定位
app.config.errorHandler = (err, instance, info) => {
  console.error('[vue error] route=' + window.location.hash + ' info=' + info, err)
}

// Vue 只管得到组件内部的异常。HMR 残留模块、第三方脚本、定时器回调里抛出的错误
// 不会走 errorHandler，控制台里只剩一行匿名堆栈。
// 这里补一个全局监听，把出错脚本的地址和行列号打出来，方便判断是不是自己的代码。
window.addEventListener('error', (event) => {
  // 资源加载失败（比如图片 404）没有 error 对象，交给浏览器默认输出即可
  if (!event.error) return
  console.error(
    `[window error] route=${window.location.hash} file=${event.filename} line=${event.lineno}:${event.colno}`,
    event.error
  )
})

app.use(createPinia()).use(Vant).use(router).mount('#app')
