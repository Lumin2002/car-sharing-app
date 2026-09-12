<template>
  <div class="page">
    <van-tabs v-model:active="tabIndex" @change="onTabChange">
      <van-tab title="实名认证" />
      <van-tab title="驾照认证" />
    </van-tabs>

    <van-dropdown-menu>
      <van-dropdown-item v-model="status" :options="statusOptions" @change="reload" />
    </van-dropdown-menu>

    <van-empty v-if="!loading && list.length === 0" image="search" description="暂无认证记录" />

    <div v-for="item in list" :key="item.id" class="card-block">
      <div class="row-between">
        <div class="title">{{ tabIndex === 0 ? item.realName : item.licenseNo }}</div>
        <van-tag :type="authStatusTag[item.status]">
          {{ authStatusText[item.status] || item.status }}
        </van-tag>
      </div>

      <div class="muted mt6">用户 #{{ item.userId }}</div>

      <template v-if="tabIndex === 0">
        <div class="muted mt4">身份证号：{{ item.idCardNo }}</div>
      </template>
      <template v-else>
        <div class="muted mt4">
          准驾车型：{{ item.licenseClass || '-' }} · 有效期 {{ item.issueDate || '-' }} ~
          {{ item.expireDate || '-' }}
        </div>
        <div class="muted mt4">
          <van-tag v-if="item.expired" type="danger" size="mini">驾照已过期</van-tag>
        </div>
      </template>

      <div class="muted mt4">提交时间：{{ formatDateTime(item.submitTime) }}</div>
      <div class="muted mt4" v-if="item.rejectReason">驳回原因：{{ item.rejectReason }}</div>

      <div class="thumbs">
        <img
          v-for="(url, i) in images(item)"
          :key="i"
          :src="url"
          class="thumb"
          @click="preview(item, url)"
        />
      </div>

      <div class="item-actions" v-if="auth.isAdmin && item.status === 'PENDING'">
        <van-button size="small" type="success" @click="approve(item)">通过</van-button>
        <van-button size="small" type="danger" @click="openReject(item)">驳回</van-button>
      </div>
    </div>

    <div v-if="list.length" class="load-more">
      <span v-if="finished">共 {{ total }} 条，没有更多了</span>
      <van-button v-else size="small" plain type="primary" :loading="loading" @click="loadMore">
        加载更多
      </van-button>
    </div>

    <van-dialog
      v-model:show="rejectVisible"
      title="驳回原因"
      show-cancel-button
      @confirm="doReject"
    >
      <van-field v-model="rejectReason" rows="2" autosize type="textarea" placeholder="必填" />
    </van-dialog>

    <van-image-preview v-model:show="previewVisible" :images="previewImages" :start-position="previewIndex" />
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { showToast, showConfirmDialog } from 'vant'
import { verificationApi } from '@shared/api'
import { useAuthStore } from '@shared/store/auth'
import { authStatusText, authStatusTag, formatDateTime } from '@shared/utils/dict'

const auth = useAuthStore()
const tabIndex = ref(0)
const status = ref('PENDING')

const statusOptions = [
  { text: '待审核', value: 'PENDING' },
  { text: '已通过', value: 'APPROVED' },
  { text: '已驳回', value: 'REJECTED' },
  { text: '全部', value: '' }
]

const list = ref([])
const pageNum = ref(1)
const pageSize = 10
const total = ref(0)
const loading = ref(false)
const finished = ref(false)

const rejectVisible = ref(false)
const rejectReason = ref('')
const rejectTarget = ref(null)

const previewVisible = ref(false)
const previewImages = ref([])
const previewIndex = ref(0)

const images = (item) =>
  (tabIndex.value === 0
    ? [item.idCardFront, item.idCardBack]
    : [item.licenseFront, item.licenseBack]
  ).filter(Boolean)

async function fetchPage(append = false) {
  loading.value = true
  try {
    const params = { pageNum: pageNum.value, pageSize, status: status.value }
    const data =
      tabIndex.value === 0
        ? await verificationApi.realnamePage(params)
        : await verificationApi.licensePage(params)
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

function onTabChange() {
  reload()
}

function preview(item, url) {
  previewImages.value = images(item)
  previewIndex.value = Math.max(0, previewImages.value.indexOf(url))
  previewVisible.value = true
}

function approve(item) {
  showConfirmDialog({ title: '审核通过', message: '确认通过该认证吗？' })
    .then(() => audit(item, true, ''))
    .catch(() => {})
}

function openReject(item) {
  rejectTarget.value = item
  rejectReason.value = ''
  rejectVisible.value = true
}

function doReject() {
  if (!rejectReason.value.trim()) {
    showToast('驳回必须填写原因')
    return
  }
  audit(rejectTarget.value, false, rejectReason.value.trim())
}

async function audit(item, approved, reason) {
  try {
    if (tabIndex.value === 0) {
      await verificationApi.auditRealname(item.id, approved, reason)
    } else {
      await verificationApi.auditLicense(item.id, approved, reason)
    }
    showToast({ type: 'success', message: approved ? '已通过' : '已驳回' })
    reload()
  } catch (e) {
    showToast(e.message || '操作失败')
  }
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

.thumbs {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.thumb {
  width: 84px;
  height: 56px;
  border-radius: 6px;
  object-fit: cover;
  border: 1px solid #eef1f6;
  cursor: pointer;
}

.load-more {
  text-align: center;
  padding: 10px 0 20px;
  color: #8a8f99;
  font-size: 13px;
}
</style>
