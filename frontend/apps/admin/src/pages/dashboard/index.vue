<template>
  <div class="page">
    <div class="card-block" style="display: flex; align-items: center; gap: 12px">
      <div class="avatar">{{ avatarText }}</div>
      <div>
        <div style="font-size: 17px; font-weight: 600">{{ auth.user?.username || '-' }}</div>
        <div class="muted">{{ roleText[auth.role] || auth.role }} · {{ auth.user?.phone || '-' }}</div>
      </div>
    </div>

    <div class="stat-grid">
      <div v-for="s in statList" :key="s.label" class="stat-cell">
        <div class="stat-num" :class="s.cls">{{ s.value }}</div>
        <div class="muted">{{ s.label }}</div>
      </div>
    </div>

    <van-cell-group inset title="快捷入口">
      <van-cell title="车辆管理" icon="logistics" is-link @click="go('/pages/car/index')" />
      <van-cell title="订单管理" icon="orders-o" is-link @click="go('/pages/order/index')" />
      <van-cell title="用户管理" icon="friends-o" is-link @click="go('/pages/user/index')" />
      <van-cell icon="user-o" is-link @click="go('/pages/verify/index')">
        <template #title>
			<span>认证审核</span>
          <van-tag v-if="stats.pendingVerify" class="ml6" type="warning" size="mini">
            {{ stats.pendingVerify }}
          </van-tag>
        </template>
      </van-cell>
      <van-cell title="操作日志" icon="records" is-link @click="go('/pages/log/index')" />
    </van-cell-group>

    <div v-if="loading" class="muted" style="text-align: center; padding: 12px">统计加载中…</div>
  </div>
</template>

<script setup>
import { computed, reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { carApi, rentalApi, userApi, verificationApi } from '@shared/api'
import { useAuthStore } from '@shared/store/auth'
import { roleText } from '@shared/utils/dict'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const stats = reactive({
  carTotal: 0,
  carFree: 0,
  carRented: 0,
  orderTotal: 0,
  renting: 0,
  userTotal: 0,
  pendingVerify: 0
})

const avatarText = computed(() => (auth.user?.username || 'A').slice(0, 1).toUpperCase())

const statList = computed(() => [
  { label: '车辆总数', value: stats.carTotal },
  { label: '空闲车辆', value: stats.carFree, cls: 'green' },
  { label: '已租出', value: stats.carRented, cls: 'red' },
  { label: '订单总数', value: stats.orderTotal },
  { label: '租用中', value: stats.renting, cls: 'green' },
  { label: '用户数', value: auth.isAdmin ? stats.userTotal : '-' }
])

function go(path) {
  router.replace(path)
}

async function loadStats() {
  loading.value = true
  try {
    const [carTotal, carFree, carRented, orderTotal, renting] = await Promise.all([
      carApi.page({ pageNum: 1, pageSize: 1 }),
      carApi.page({ pageNum: 1, pageSize: 1, status: 'FREE' }),
      carApi.page({ pageNum: 1, pageSize: 1, status: 'RENTED' }),
      rentalApi.page({ pageNum: 1, pageSize: 1 }),
      rentalApi.page({ pageNum: 1, pageSize: 1, status: 'RENTING' })
    ])
    stats.carTotal = carTotal.total || 0
    stats.carFree = carFree.total || 0
    stats.carRented = carRented.total || 0
    stats.orderTotal = orderTotal.total || 0
    stats.renting = renting.total || 0

    if (auth.isAdmin) {
      try {
        const users = await userApi.page({ pageNum: 1, pageSize: 1 })
        stats.userTotal = users.total || 0
      } catch (e) {
        stats.userTotal = 0
      }
      // 待审核认证数量：实名 + 驾照
      try {
        const [realname, license] = await Promise.all([
          verificationApi.realnamePage({ pageNum: 1, pageSize: 1, status: 'PENDING' }),
          verificationApi.licensePage({ pageNum: 1, pageSize: 1, status: 'PENDING' })
        ])
        stats.pendingVerify = (realname.total || 0) + (license.total || 0)
      } catch (e) {
        stats.pendingVerify = 0
      }
    }
  } catch (e) {
    showToast(e.message || '统计加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  if (!auth.user) {
    try {
      await auth.fetchCurrent()
    } catch (e) {
      // 忽略
    }
  }
  loadStats()
})
</script>

<style scoped>
.ml6 {
  margin-left: 6px;
}

.avatar {
  width: 46px;
  height: 46px;
  border-radius: 50%;
  background: linear-gradient(135deg, #1989fa, #6aa1ff);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 19px;
  font-weight: 700;
}
</style>
