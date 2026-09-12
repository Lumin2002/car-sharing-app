<template>
  <div class="page">
    <div v-if="auth.user" class="card-block user-card">
      <div class="avatar">{{ avatarText }}</div>
      <div>
        <div class="uname">{{ auth.user.username }}</div>
        <div class="muted">
          {{ auth.user.phone }} · {{ roleText[auth.user.role] || auth.user.role }}
        </div>
      </div>
    </div>

    <div v-else class="card-block guest-card">
      <div class="guest-avatar">🚗</div>
      <div class="guest-title">登录后即可租车</div>
      <div class="muted">查看订单、修改资料等都需要先登录</div>
      <div class="guest-actions">
        <van-button block type="primary" @click="uni.navigateTo('/pages/login/index')">登录</van-button>
        <van-button block plain type="primary" @click="uni.navigateTo('/pages/register/index')">
          注册
        </van-button>
      </div>
    </div>

    <van-cell-group inset v-if="auth.isLogin">
      <van-cell title="我的订单" is-link @click="uni.navigateTo('/pages/order/list')" />
      <van-cell is-link @click="uni.navigateTo('/pages/profile/verify')">
        <template #title>
          实名 / 驾照认证
          <van-tag v-if="!verifyReady" class="ml6" type="warning" plain size="mini">
            未完成
          </van-tag>
        </template>
      </van-cell>
      <van-cell is-link @click="uni.navigateTo('/pages/message/index')">
        <template #title>
          消息中心
          <van-tag v-if="unread" class="ml6" type="danger" size="mini">{{ unread }}</van-tag>
        </template>
      </van-cell>
      <van-cell title="修改密码" is-link @click="uni.navigateTo('/pages/profile/password')" />
    </van-cell-group>

    <div class="logout-area" v-if="auth.isLogin">
      <van-button block type="danger" @click="logout">退出登录</van-button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { showToast, showConfirmDialog } from 'vant'
import uni from '@shared/utils/uni'
import { useAuthStore } from '@shared/store/auth'
import { messageApi, verificationApi } from '@shared/api'
import { roleText } from '@shared/utils/dict'

const auth = useAuthStore()
const verifyReady = ref(true)
const unread = ref(0)
const avatarText = computed(() => (auth.user?.username || 'U').slice(0, 1).toUpperCase())

async function loadBadges() {
  if (!auth.isLogin) return
  try {
    const summary = await verificationApi.me()
    verifyReady.value = summary.rentReady
  } catch (e) {
    verifyReady.value = true
  }
  try {
    unread.value = await messageApi.unreadCount()
  } catch (e) {
    unread.value = 0
  }
}

function logout() {
  showConfirmDialog({ title: '退出登录', message: '确定要退出当前账号吗？' })
    .then(async () => {
      await auth.logout()
      showToast('已退出')
      uni.reLaunch('/pages/login/index')
    })
    .catch(() => {})
}

onMounted(async () => {
  if (auth.isLogin) {
    try {
      await auth.fetchCurrent()
    } catch (e) {
      // token 失效时 request 层会跳登录
    }
    loadBadges()
  }
})
</script>

<style scoped>
.ml6 {
  margin-left: 6px;
}

.user-card {
  display: flex;
  align-items: center;
  gap: 12px;
}

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

.uname {
  font-size: 17px;
  font-weight: 600;
}

.logout-area {
  margin: 20px 16px;
}
</style>
