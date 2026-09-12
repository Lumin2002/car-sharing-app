import { defineStore } from 'pinia'
import { authApi } from '../api'
import { getAccessToken, setTokens, clearTokens } from '../utils/token'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: getAccessToken(),
    user: null
  }),
  getters: {
    isLogin: (state) => !!state.token,
    role: (state) => (state.user && state.user.role) || '',
    isAdmin: (state) => !!state.user && state.user.role === 'ADMIN',
    isOperations: (state) => !!state.user && state.user.role === 'OPERATIONS',
    /** 运维端可写操作：只有管理员能改数据 */
    canManage: (state) => !!state.user && state.user.role === 'ADMIN'
  },
  actions: {
    async login(form) {
      const data = await authApi.login(form)
      setTokens(data)
      this.token = data.accessToken
      await this.fetchCurrent()
      return data
    },
    async fetchCurrent() {
      try {
        this.user = await authApi.current()
        return this.user
      } catch (e) {
        this.user = null
        throw e
      }
    },
    async logout() {
      try {
        await authApi.logout()
      } catch (e) {
        // 登出失败也继续清理本地状态
      }
      this.clear()
    },
    clear() {
      clearTokens()
      this.token = ''
      this.user = null
    }
  }
})
