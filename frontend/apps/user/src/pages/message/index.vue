<template>
  <div class="page">
    <van-tabs v-model:active="activeIndex" @change="reload">
      <van-tab v-for="t in tabs" :key="t.value" :title="t.label" />
    </van-tabs>

    <div class="toolbar">
      <span class="muted">未读 {{ unread }} 条</span>
      <van-button size="mini" plain type="primary" :disabled="!unread" @click="readAll">
        全部已读
      </van-button>
    </div>

    <van-empty v-if="!loading && list.length === 0" image="search" description="暂无消息" />

    <van-swipe-cell v-for="item in list" :key="item.id">
      <div class="card-block msg" :class="{ unread: item.readStatus === 0 }" @click="open(item)">
        <div class="msg-head">
          <van-icon :name="messageTypeIcon[item.type] || 'chat-o'" size="18" />
          <span class="msg-title">{{ item.title }}</span>
          <span class="muted">{{ formatDateTime(item.createTime) }}</span>
        </div>
        <div class="muted msg-content">{{ item.content }}</div>
        <van-tag v-if="item.readStatus === 0" type="danger" plain size="mini">未读</van-tag>
      </div>
      <template #right>
        <van-button square type="danger" text="删除" class="swipe-btn" @click="remove(item)" />
      </template>
    </van-swipe-cell>

    <div v-if="list.length" class="load-more">
      <span v-if="finished">共 {{ total }} 条，没有更多了</span>
      <van-button v-else size="small" plain type="primary" :loading="loading" @click="loadMore">
        加载更多
      </van-button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { showToast, showConfirmDialog } from 'vant'
import uni from '@shared/utils/uni'
import { messageApi } from '@shared/api'
import { formatDateTime, messageTypeIcon } from '@shared/utils/dict'

const tabs = [
  { label: '全部', value: '' },
  { label: '未读', value: 'UNREAD' },
  { label: '已读', value: 'READ' }
]
const activeIndex = ref(0)
const readStatus = computed(() => (tabs[activeIndex.value] || tabs[0]).value)

const list = ref([])
const pageNum = ref(1)
const pageSize = 10
const total = ref(0)
const unread = ref(0)
const loading = ref(false)
const finished = ref(false)

async function fetchUnread() {
  try {
    unread.value = await messageApi.unreadCount()
  } catch (e) {
    unread.value = 0
  }
}

async function fetchPage(append = false) {
  loading.value = true
  try {
    const data = await messageApi.my({
      pageNum: pageNum.value,
      pageSize,
      readStatus: readStatus.value
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
  fetchUnread()
}

function loadMore() {
  if (finished.value) return
  pageNum.value += 1
  fetchPage(true)
}

async function open(item) {
  if (item.readStatus === 0) {
    try {
      await messageApi.read(item.id)
      item.readStatus = 1
      fetchUnread()
    } catch (e) {
      // 标记失败不影响查看
    }
  }
  if (item.orderId) {
    uni.navigateTo(`/pages/order/detail?id=${item.orderId}`)
  }
}

function readAll() {
  messageApi
    .readAll()
    .then(() => {
      showToast({ type: 'success', message: '已全部标记为已读' })
      reload()
    })
    .catch((e) => showToast(e.message || '操作失败'))
}

function remove(item) {
  showConfirmDialog({ title: '删除消息', message: '删除后不可恢复，确定吗？' })
    .then(() => messageApi.remove(item.id))
    .then(() => {
      showToast({ type: 'success', message: '已删除' })
      reload()
    })
    .catch((e) => {
      if (e && e.message && e.message !== 'cancel') showToast(e.message)
    })
}

onMounted(reload)
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px 0;
}

.msg {
  cursor: pointer;
}

.msg.unread .msg-title {
  font-weight: 700;
}

.msg-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.msg-title {
  flex: 1;
  font-weight: 600;
}

.msg-content {
  margin-top: 6px;
  line-height: 1.5;
}

.swipe-btn {
  height: 100%;
}

.load-more {
  text-align: center;
  padding: 10px 0 20px;
  color: #8a8f99;
  font-size: 13px;
}
</style>
