<template>
  <div class="page">
    <van-form @submit="submit">
      <van-cell-group inset>
        <van-field
          v-model="form.username"
          label="用户名"
          placeholder="请输入用户名"
          :rules="[{ required: true, message: '请输入用户名' }]"
        />
        <van-field
          v-model="form.phone"
          label="手机号"
          placeholder="请输入手机号"
          :rules="[{ required: true, message: '请输入手机号' }]"
        />
        <van-field
          v-model="form.password"
          type="password"
          label="密码"
          placeholder="6-32位密码"
          :rules="[{ required: true, message: '请输入密码' }]"
        />
        <van-field
          v-model="form.confirmPassword"
          type="password"
          label="确认密码"
          placeholder="请再次输入密码"
          :rules="[{ required: true, message: '请再次输入密码' }]"
        />
      </van-cell-group>

      <div class="form-submit">
        <van-button round block type="primary" native-type="submit" :loading="loading">
          注册
        </van-button>
      </div>
    </van-form>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { showToast } from 'vant'
import uni from '@shared/utils/uni'
import { authApi } from '@shared/api'

const loading = ref(false)
const form = reactive({ username: '', phone: '', password: '', confirmPassword: '' })

async function submit() {
  if (form.password !== form.confirmPassword) {
    showToast('两次密码不一致')
    return
  }
  loading.value = true
  try {
    await authApi.register(form)
    showToast({ type: 'success', message: '注册成功' })
    setTimeout(() => uni.navigateBack(), 800)
  } catch (e) {
    showToast(e.message || '注册失败')
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
