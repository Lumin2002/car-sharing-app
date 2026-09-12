<template>
  <div class="page">
    <van-form @submit="submit">
      <van-cell-group inset>
        <van-field
          v-model="form.oldPassword"
          type="password"
          label="旧密码"
          placeholder="请输入旧密码"
          :rules="[{ required: true, message: '请输入旧密码' }]"
        />
        <van-field
          v-model="form.newPassword"
          type="password"
          label="新密码"
          placeholder="6-32位新密码"
          :rules="[{ required: true, message: '请输入新密码' }]"
        />
        <van-field
          v-model="form.confirmPassword"
          type="password"
          label="确认新密码"
          placeholder="请再次输入新密码"
          :rules="[{ required: true, message: '请再次输入新密码' }]"
        />
      </van-cell-group>

      <div class="submit-area">
        <van-button round block type="primary" native-type="submit" :loading="loading">
          确认修改
        </van-button>
      </div>
    </van-form>

    <div class="tip muted">修改成功后所有登录状态会失效，需要重新登录</div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { showToast } from 'vant'
import uni from '@shared/utils/uni'
import { authApi } from '@shared/api'
import { useAuthStore } from '@shared/store/auth'

const auth = useAuthStore()
const loading = ref(false)
const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

async function submit() {
  if (form.newPassword !== form.confirmPassword) {
    showToast('两次新密码不一致')
    return
  }
  loading.value = true
  try {
    await authApi.changePassword({
      oldPassword: form.oldPassword,
      newPassword: form.newPassword
    })
    showToast({ type: 'success', message: '修改成功，请重新登录' })
    auth.clear()
    setTimeout(() => uni.reLaunch('/pages/login/index'), 900)
  } catch (e) {
    showToast(e.message || '修改失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.submit-area {
  margin: 16px;
}

.tip {
  text-align: center;
}
</style>
