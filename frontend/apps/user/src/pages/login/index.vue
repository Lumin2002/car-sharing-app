<template>
  <div class="page">
    <div class="login-brand">
      <div class="login-logo">🚗</div>
      <h1 class="login-title">共享汽车</h1>
      <p class="login-sub">登录后即可租车</p>
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

    <div class="login-links" @click="uni.navigateTo('/pages/register/index')">
      还没有账号？去注册
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { showToast } from 'vant'
import uni from '@shared/utils/uni'
import { useAuthStore } from '@shared/store/auth'

const auth = useAuthStore()
const loading = ref(false)
const form = reactive({ phone: '', password: '' })

async function submit() {
  loading.value = true
  try {
    await auth.login({ phone: form.phone, password: form.password })
    showToast({ type: 'success', message: '登录成功' })
    uni.reLaunch('/pages/home/index')
  } catch (e) {
    showToast(e.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.form-submit {
  margin: 16px;
}
</style>
