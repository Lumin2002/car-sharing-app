<template>
  <div class="page">
    <div class="login-brand">
      <div class="login-logo">🛠️</div>
      <h1 class="login-title">共享汽车 · 运维端</h1>
      <p class="login-sub">仅限管理员 / 运维人员登录</p>
    </div>

    <van-form @submit="submit">
      <van-cell-group inset>
        <van-field
          v-model="form.phone"
          name="phone"
          label="手机号"
          placeholder="请输入手机号"
          :rules="[{ required: true, message: '请输入手机号' }]"
        />
        <van-field
          v-model="form.password"
          type="password"
          name="password"
          label="密码"
          placeholder="请输入密码"
          :rules="[{ required: true, message: '请输入密码' }]"
        />
      </van-cell-group>

      <div class="form-submit">
        <van-button round block type="primary" native-type="submit" :loading="loading">
          登录
        </van-button>
      </div>
    </van-form>

    <div class="muted" style="text-align: center; margin-top: 16px">
      当前没有账号？请联系管理员在后台创建
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { useAuthStore } from '@shared/store/auth'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const form = reactive({ phone: '', password: '' })

async function submit() {
  loading.value = true
  try {
    await auth.login({ phone: form.phone, password: form.password })
    if (!auth.isAdmin && !auth.isOperations) {
      auth.clear()
      showToast('该账号不是管理 / 运维角色')
      return
    }
    showToast({ type: 'success', message: '登录成功' })
    router.replace('/pages/dashboard/index')
  } catch (e) {
    showToast(e.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-brand {
  text-align: center;
  padding: 40px 0 20px;
}

.login-logo {
  font-size: 52px;
}

.login-title {
  font-size: 20px;
  margin: 8px 0 4px;
}

.login-sub {
  color: #8a8f99;
  font-size: 13px;
  margin: 0;
}

.form-submit {
  margin: 16px;
}
</style>
