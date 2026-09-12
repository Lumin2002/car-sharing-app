/**
 * uni API 兼容层。
 *
 * 目的：让页面里可以按 uni-app 的习惯写 `uni.request` / `uni.navigateTo` /
 * `uni.showToast` / `uni.getStorageSync`，以后迁移到真正的 uni-app 工程时，
 * 页面代码基本不用改（删掉这个文件、换成 uni-app 内置的 uni 即可）。
 */

let router = null

/** 由 main.js 注入 vue-router 实例 */
export function registerRouter(instance) {
  router = instance
}

function normalize(url) {
  return url.startsWith('/') ? url : '/' + url
}

function nav(method, url) {
  if (!router) return
  router[method](normalize(url))
}

const STORAGE_PREFIX = 'cs_'

function toast(title, icon = 'none', duration = 2000) {
  const el = document.createElement('div')
  el.textContent = title
  el.style.cssText = [
    'position:fixed',
    'left:50%',
    'top:50%',
    'transform:translate(-50%,-50%)',
    'max-width:70%',
    'padding:10px 16px',
    'border-radius:8px',
    'background:rgba(0,0,0,.78)',
    'color:#fff',
    'font-size:14px',
    'line-height:1.4',
    'text-align:center',
    'z-index:9999'
  ].join(';')
  document.body.appendChild(el)
  setTimeout(() => el.remove(), duration)
}

const uni = {
  request(options = {}) {
    const { url, method = 'GET', data, header = {} } = options
    const upper = method.toUpperCase()
    const isFormData = typeof FormData !== 'undefined' && data instanceof FormData

    let finalUrl = url
    let body
    if (upper === 'GET' || upper === 'DELETE') {
      if (data && Object.keys(data).length) {
        const query = new URLSearchParams(
          Object.entries(data).filter(([, v]) => v !== undefined && v !== null && v !== '')
        ).toString()
        if (query) finalUrl += (finalUrl.includes('?') ? '&' : '?') + query
      }
    } else if (isFormData) {
      // 文件上传：body 直接用 FormData，且不能手动设置 Content-Type
      // （multipart 的 boundary 必须由浏览器生成）
      body = data
    } else {
      body = data === undefined ? undefined : JSON.stringify(data)
    }

    const finalHeader = isFormData ? { ...header } : { 'Content-Type': 'application/json', ...header }

    return fetch(finalUrl, { method: upper, headers: finalHeader, body })
      .then(async (res) => {
        const text = await res.text()
        let parsed = null
        try {
          parsed = text ? JSON.parse(text) : null
        } catch (e) {
          parsed = text
        }
        const result = { statusCode: res.status, data: parsed }
        options.success && options.success(result)
        options.complete && options.complete(result)
        return result
      })
      .catch((err) => {
        options.fail && options.fail(err)
        options.complete && options.complete(err)
        throw err
      })
  },

  /**
   * 选择本地图片（H5 实现）。
   * 返回的 tempFiles[i].file 是浏览器 File 对象，可直接交给 fileApi.upload；
   * 迁移到真正的 uni-app 时这里会换成 uni 内置实现，页面代码不用改。
   */
  chooseImage({ count = 1 } = {}) {
    return new Promise((resolve, reject) => {
      const input = document.createElement('input')
      input.type = 'file'
      input.accept = 'image/*'
      if (count > 1) input.multiple = true
      input.style.display = 'none'
      document.body.appendChild(input)

      input.onchange = () => {
        const files = Array.from(input.files || [])
        document.body.removeChild(input)
        if (!files.length) {
          reject(new Error('cancel'))
          return
        }
        resolve({
          tempFiles: files.map((file) => ({
            file,
            size: file.size,
            path: URL.createObjectURL(file)
          })),
          tempFilePaths: files.map((file) => URL.createObjectURL(file))
        })
      }

      input.click()
    })
  },

  navigateTo(url) {
    nav('push', url)
  },
  redirectTo(url) {
    nav('replace', url)
  },
  reLaunch(url) {
    nav('replace', url)
  },
  navigateBack() {
    if (router) router.back()
  },
  switchTab(url) {
    nav('push', url)
  },

  showToast({ title, icon = 'none', duration = 2000 }) {
    toast(title, icon, duration)
  },
  showModal({ title, content, success }) {
    const confirmed = window.confirm(title ? `${title}\n\n${content || ''}` : content || '')
    success && success({ confirm: confirmed, cancel: !confirmed })
  },

  setStorageSync(key, value) {
    localStorage.setItem(STORAGE_PREFIX + key, JSON.stringify(value))
  },
  getStorageSync(key) {
    const raw = localStorage.getItem(STORAGE_PREFIX + key)
    if (raw === null) return ''
    try {
      return JSON.parse(raw)
    } catch (e) {
      return raw
    }
  },
  removeStorageSync(key) {
    localStorage.removeItem(STORAGE_PREFIX + key)
  }
}

export default uni
