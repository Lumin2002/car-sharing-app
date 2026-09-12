<template>
  <div class="page">
    <van-search v-model="keyword" placeholder="搜索操作内容 / IP" @search="reload" @clear="reload" />

    <van-dropdown-menu>
      <van-dropdown-item v-model="results" :options="resultOptions" @change="reload" />
      <van-dropdown-item v-model="operType" :options="typeOptions" @change="reload" />
    </van-dropdown-menu>

    <van-empty v-if="!loading && list.length === 0" image="search" description="暂无日志" />

    <div v-for="item in list" :key="item.id" class="card-block">
      <div class="row-between">
        <div class="title">{{ item.operDesc }}</div>
        <van-tag :type="item.results === 0 ? 'success' : 'danger'">
          {{ item.results === 0 ? '成功' : '失败' }}
        </van-tag>
      </div>
      <div class="muted mt6">
        {{ item.operType }} · 用户 #{{ item.userId ?? '-' }} · {{ formatDateTime(item.operTime) }}
      </div>
      <div class="muted mt4">IP：{{ item.ip || '-' }}</div>
      <div class="muted mt4" v-if="item.msg">信息：{{ item.msg }}</div>
    </div>

    <div v-if="list.length" class="load-more">
      <span v-if="finished">共 {{ total }} 条，没有更多了</span>
      <van-button v-else size="small" plain type="primary" :loading="loading" @click="loadMore">
        加载更多
      </van-button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { showToast } from 'vant'
import { logApi } from '@shared/api'
import { formatDateTime } from '@shared/utils/dict'

const keyword = ref('')
const results = ref('')
const operType = ref('')

const resultOptions = [
  { text: '全部结果', value: '' },
  { text: '成功', value: '0' },
  { text: '失败', value: '1' }
]

const typeOptions = [
  { text: '全部类型', value: '' },
  { text: '认证 AUTH', value: 'AUTH' },
  { text: '车辆 CAR', value: 'CAR' },
  { text: '门店 STORE', value: 'STORE' },
  { text: '租赁 RENTAL', value: 'RENTAL' },
  { text: '支付 PAYMENT', value: 'PAYMENT' },
  { text: '结算 SETTLEMENT', value: 'SETTLEMENT' },
  { text: '用户 USER', value: 'USER' },
  { text: '认证审核 VERIFY', value: 'VERIFY' },
  { text: '消息 MESSAGE', value: 'MESSAGE' }
]

const list = ref([])
const pageNum = ref(1)
const pageSize = 10
const total = ref(0)
const loading = ref(false)
const finished = ref(false)

async function fetchPage(append = false) {
  loading.value = true
  try {
    const data = await logApi.page({
      pageNum: pageNum.value,
      pageSize,
      keyword: keyword.value,
      results: results.value,
      operType: operType.value
    })
    const records = data.records || []
    list.value = append ? list.value.concat(records) : records
    total.value = data.total || 0
    finished.value = list.value.length >= total.value || records.length < pageSize
  } catch (e) {
    showToast(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function reload() {
  pageNum.value = 1
  finished.value = false
  fetchPage(false)
}

function loadMore() {
  if (finished.value) return
  pageNum.value += 1
  fetchPage(true)
}

onMounted(reload)
</script>

<style scoped>
.title {
  font-size: 15px;
  font-weight: 600;
}

.mt4 {
  margin-top: 4px;
}

.mt6 {
  margin-top: 6px;
}

</style>
