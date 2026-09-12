<template>
  <div class="page">
    <van-search v-model="keyword" placeholder="搜索门店名称 / 地址" @search="reload" />

    <van-dropdown-menu>
      <van-dropdown-item v-model="status" :options="statusOptions" @change="reload" />
    </van-dropdown-menu>

    <div class="add-bar" v-if="auth.canManage">
      <van-button block type="primary" icon="plus" @click="openForm()">新增门店</van-button>
    </div>

    <van-empty v-if="!loading && stores.length === 0" image="search" description="没有门店" />

    <div v-for="store in stores" :key="store.storeId" class="card-block">
      <div class="row-between">
        <div style="font-size: 15px; font-weight: 600">{{ store.name }}</div>
        <van-tag :type="store.status === 'OPEN' ? 'success' : 'default'">
          {{ storeStatusText[store.status] || store.status }}
        </van-tag>
      </div>
      <div class="muted mt4">{{ store.address || '未填写地址' }}</div>
      <div class="muted mt4">
        🚗 车辆 {{ store.totalCarCount || 0 }} 辆 · 可租
        <span style="color: #07c160; font-weight: 600">{{ store.rentableCarCount || 0 }}</span> 辆
      </div>
      <div class="muted mt4">
        📍 {{ store.longitude }}, {{ store.latitude }}
      </div>
      <div class="muted mt4" v-if="store.phone || store.businessHours">
        {{ store.phone || '' }} {{ store.businessHours ? '· ' + store.businessHours : '' }}
      </div>
      <div class="item-actions" v-if="auth.canManage">
        <van-button size="small" plain type="primary" @click="openForm(store)">编辑</van-button>
        <van-button size="small" plain type="danger" @click="remove(store)">删除</van-button>
      </div>
    </div>

    <div v-if="stores.length" class="load-more">
      <span v-if="finished">共 {{ total }} 家，没有更多了</span>
      <van-button v-else size="small" plain type="primary" :loading="loading" @click="loadMore">
        加载更多
      </van-button>
    </div>

    <van-dialog
      v-model:show="showForm"
      :title="form.storeId ? '编辑门店' : '新增门店'"
      :show-confirm-button="false"
      :show-cancel-button="false"
    >
      <div class="form-scroll">
        <van-cell-group inset>
          <van-field v-model="form.name" label="门店名称" placeholder="必填" />
          <van-field v-model="form.address" label="详细地址" placeholder="选填" />
          <van-field v-model.number="form.longitude" label="经度" placeholder="必填，如 113.264385" />
          <van-field v-model.number="form.latitude" label="纬度" placeholder="必填，如 23.129112" />
          <van-field v-model="form.phone" label="联系电话" placeholder="选填" />
          <van-field v-model="form.businessHours" label="营业时间" placeholder="如 08:00-22:00" />
          <van-field
            :model-value="storeStatusText[form.status]"
            readonly
            is-link
            label="状态"
            @click="openPicker"
          />
        </van-cell-group>
      </div>
      <div class="dialog-footer">
        <van-button block plain @click="showForm = false">取消</van-button>
        <van-button block type="primary" :loading="saving" @click="save">保存</van-button>
      </div>
    </van-dialog>

    <van-popup v-model:show="pickerVisible" position="bottom" round>
      <van-picker
        :columns="pickerColumns"
        @confirm="onPickerConfirm"
        @cancel="pickerVisible = false"
      />
    </van-popup>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { showToast, showConfirmDialog } from 'vant'
import { storeApi } from '@shared/api'
import { useAuthStore } from '@shared/store/auth'
import { storeStatusText } from '@shared/utils/dict'

const auth = useAuthStore()

const stores = ref([])
const keyword = ref('')
const status = ref('')
const statusOptions = [
  { text: '全部状态', value: '' },
  { text: '营业中', value: 'OPEN' },
  { text: '已打烊', value: 'CLOSED' }
]

const pageNum = ref(1)
const pageSize = 10
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const finished = ref(false)
const showForm = ref(false)

const pickerVisible = ref(false)
const pickerColumns = ref([])

const emptyForm = () => ({
  storeId: null,
  name: '',
  address: '',
  longitude: null,
  latitude: null,
  phone: '',
  businessHours: '08:00-22:00',
  status: 'OPEN'
})
const form = reactive(emptyForm())

function openPicker() {
  pickerColumns.value = Object.keys(storeStatusText).map((k) => ({
    text: storeStatusText[k],
    value: k
  }))
  pickerVisible.value = true
}

function onPickerConfirm({ selectedOptions }) {
  if (selectedOptions && selectedOptions[0]) {
    form.status = selectedOptions[0].value
  }
  pickerVisible.value = false
}

async function fetchPage(append = false) {
  loading.value = true
  try {
    const data = await storeApi.page({
      pageNum: pageNum.value,
      pageSize,
      keyword: keyword.value,
      status: status.value
    })
    const records = data.records || []
    stores.value = append ? stores.value.concat(records) : records
    total.value = data.total || 0
    finished.value = stores.value.length >= total.value || records.length < pageSize
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

function openForm(store) {
  Object.assign(form, emptyForm(), store || {})
  showForm.value = true
}

async function save() {
  if (!form.name || form.longitude == null || form.latitude == null) {
    showToast('门店名称、经度、纬度必填')
    return
  }
  saving.value = true
  const payload = { ...form }
  delete payload.storeId
  delete payload.rentableCarCount
  delete payload.totalCarCount
  try {
    if (form.storeId) {
      await storeApi.update(form.storeId, payload)
    } else {
      await storeApi.add(payload)
    }
    showToast({ type: 'success', message: '保存成功' })
    showForm.value = false
    reload()
  } catch (e) {
    showToast(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function remove(store) {
  showConfirmDialog({
    title: '删除门店',
    message: `确定删除「${store.name}」吗？门店下还有车辆时无法删除。`
  })
    .then(() => storeApi.remove(store.storeId))
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
.form-scroll {
  max-height: 52vh;
  overflow-y: auto;
  padding: 12px 0;
}

.dialog-footer {
  display: flex;
  gap: 10px;
  padding: 10px 16px 16px;
}
</style>
