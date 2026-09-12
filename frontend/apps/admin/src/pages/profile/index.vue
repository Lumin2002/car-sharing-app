<template>
  <div class="page">
    <div class="card-block" style="display: flex; align-items: center; gap: 12px">
      <div class="avatar">{{ avatarText }}</div>
      <div>
        <div style="font-size: 17px; font-weight: 600">{{ auth.user?.username || '-' }}</div>
        <div class="muted">{{ roleText[auth.role] || auth.role }} · {{ auth.user?.phone || '-' }}</div>
      </div>
    </div>

    <van-form @submit="submit">
      <van-cell-group inset title="修改密码">
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

    <div class="logout-area">
      <van-button block type="danger" @click="logout">退出登录</van-button>
    </div>

    <div class="muted" style="text-align: center">修改密码后需要重新登录</div>
  </div>
</template>

<script setup>
import { computed, reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import { authApi } from '@shared/api'
import { useAuthStore } from '@shared/store/auth'
import { roleText } from '@shared/utils/dict'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const avatarText = computed(() => (auth.user?.username || 'A').slice(0, 1).toUpperCase())

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
    setTimeout(() => router.replace('/pages/login/index'), 900)
  } catch (e) {
    showToast(e.message || '修改失败')
  } finally {
    loading.value = false
  }
}

function logout() {
  showConfirmDialog({ title: '退出登录', message: '确定要退出当前账号吗？' })
    .then(async () => {
      await auth.logout()
      showToast('已退出')
      router.replace('/pages/login/index')
    })
    .catch(() => {})
}

onMounted(async () => {
  if (auth.isLogin && !auth.user) {
    try {
      await auth.fetchCurrent()
    } catch (e) {
      // 忽略
    }
  }
})
</script>

<style scoped>
.avatar {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: linear-gradient(135deg, #1989fa, #6aa1ff);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  font-weight: 700;
}

.submit-area {
  margin: 16px;
}

.logout-area {
  margin: 8px 16px 16px;
}
</style>
