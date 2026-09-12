<template>
  <div class="page">
    <van-search v-model="keyword" placeholder="搜索用户名 / 手机号" @search="reload" />

    <van-dropdown-menu>
      <van-dropdown-item v-model="status" :options="statusOptions" @change="reload" />
    </van-dropdown-menu>

    <div class="add-bar">
      <van-button block type="primary" icon="plus" :disabled="!auth.isAdmin" @click="openForm()">
        新增用户
      </van-button>
    </div>

    <van-empty v-if="!loading && users.length === 0" image="search" description="没有用户" />

    <div v-for="user in users" :key="user.userId" class="card-block">
      <div class="row-between">
        <div style="font-size: 15px; font-weight: 600">{{ user.username }}</div>
        <van-tag :type="user.status === 'ENABLED' ? 'success' : 'danger'">
          {{ userStatusText[user.status] || user.status }}
        </van-tag>
      </div>
      <div class="muted mt4">{{ user.phone }} · {{ roleText[user.role] || user.role }}</div>
      <div class="muted mt4">最后登录：{{ formatDateTime(user.loginTime) }}</div>
      <div class="item-actions">
        <van-button size="small" plain type="primary" :disabled="!auth.isAdmin" @click="openForm(user)">
          编辑
        </van-button>
        <van-button
          size="small"
          plain
          :type="user.status === 'ENABLED' ? 'danger' : 'success'"
          :disabled="!auth.isAdmin"
          @click="toggleBan(user)"
        >
          {{ user.status === 'ENABLED' ? '禁用' : '启用' }}
        </van-button>
      </div>
    </div>

    <div v-if="users.length" class="load-more">
      <span v-if="finished">共 {{ total }} 个，没有更多了</span>
      <van-button v-else size="small" plain type="primary" :loading="loading" @click="loadMore">
        加载更多
      </van-button>
    </div>

    <van-dialog
      v-model:show="showForm"
      :title="form.userId ? '编辑用户' : '新增用户'"
      :show-confirm-button="false"
      :show-cancel-button="false"
    >
      <div class="form-scroll">
        <van-cell-group inset>
          <van-field v-model="form.username" label="用户名" placeholder="必填" />
          <template v-if="!form.userId">
            <van-field v-model="form.phone" label="手机号" placeholder="必填" />
            <van-field v-model="form.password" type="password" label="初始密码" placeholder="6-32位" />
          </template>
          <van-field
            :model-value="roleText[form.role]"
            readonly
            is-link
            label="角色"
            @click="openPicker('role', roleText)"
          />
          <van-field
            v-if="!form.userId"
            :model-value="userStatusText[form.status]"
            readonly
            is-link
            label="状态"
            @click="openPicker('status', userStatusText)"
          />
        </van-cell-group>
      </div>
      <div class="dialog-footer">
        <van-button block plain @click="showForm = false">取消</van-button>
        <van-button block type="primary" :loading="saving" @click="save">保存</van-button>
      </div>
    </van-dialog>

    <van-popup v-model:show="pickerVisible" position="bottom" round>
      <van-picker :columns="pickerColumns" @confirm="onPickerConfirm" @cancel="pickerVisible = false" />
    </van-popup>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { showToast, showConfirmDialog } from 'vant'
import { userApi } from '@shared/api'
import { useAuthStore } from '@shared/store/auth'
import { roleText, userStatusText, formatDateTime } from '@shared/utils/dict'

const auth = useAuthStore()

const users = ref([])
const keyword = ref('')
const status = ref('')
const statusOptions = [
  { text: '全部状态', value: '' },
  { text: '正常', value: 'ENABLED' },
  { text: '已禁用', value: 'DISABLED' }
]

const pageNum = ref(1)
const pageSize = 10
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const finished = ref(false)
const showForm = ref(false)

const pickerVisible = ref(false)
const pickerField = ref('')
const pickerColumns = ref([])

const emptyForm = () => ({
  userId: null,
  username: '',
  phone: '',
  password: '',
  role: 'USER',
  status: 'ENABLED'
})
const form = reactive(emptyForm())

function openPicker(field, dict) {
  pickerField.value = field
  pickerColumns.value = Object.keys(dict).map((k) => ({ text: dict[k], value: k }))
  pickerVisible.value = true
}

function onPickerConfirm({ selectedOptions }) {
  if (selectedOptions && selectedOptions[0]) {
    form[pickerField.value] = selectedOptions[0].value
  }
  pickerVisible.value = false
}

async function fetchPage(append = false) {
  loading.value = true
  try {
    const data = await userApi.page({
      pageNum: pageNum.value,
      pageSize,
      keyword: keyword.value,
      status: status.value
    })
    const records = data.records || []
    users.value = append ? users.value.concat(records) : records
    total.value = data.total || 0
    finished.value = users.value.length >= total.value || records.length < pageSize
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

function openForm(user) {
  Object.assign(form, emptyForm(), user || {})
  showForm.value = true
}

async function save() {
  if (!form.username) {
    showToast('用户名不能为空')
    return
  }
  saving.value = true
  try {
    if (form.userId) {
      await userApi.update(form.userId, {
        username: form.username,
        role: form.role,
        avatar: form.avatar || null
      })
      showToast({ type: 'success', message: '保存成功' })
    } else {
      if (!form.phone || !form.password) {
        showToast('手机号和初始密码必填')
        return
      }
      await userApi.add({
        username: form.username,
        phone: form.phone,
        password: form.password,
        role: form.role,
        status: form.status
      })
      showToast({ type: 'success', message: '创建成功' })
    }
    showForm.value = false
    reload()
  } catch (e) {
    showToast(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function toggleBan(user) {
  const next = user.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  const action = next === 'DISABLED' ? '禁用' : '启用'
  showConfirmDialog({ title: `${action}用户`, message: `确定要${action}「${user.username}」吗？` })
    .then(() => userApi.ban(user.userId, next))
    .then(() => {
      showToast({ type: 'success', message: `已${action}` })
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
