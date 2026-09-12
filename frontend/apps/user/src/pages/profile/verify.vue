<template>
  <div class="page">
    <div class="card-block ready-banner" :class="summary.rentReady ? 'ok' : 'warn'">
      <van-icon :name="summary.rentReady ? 'passed' : 'info-o'" size="20" />
      <div>
        <div class="ready-title">{{ summary.rentReady ? '已满足租车条件' : '还不能租车' }}</div>
        <div class="muted">
          {{
            summary.rentReady
              ? '实名认证与驾照认证均已通过'
              : '需要实名认证 + 驾照认证都通过后才能下单'
          }}
        </div>
      </div>
    </div>

    <!-- 实名认证 -->
    <van-cell-group inset :title="'实名认证 · ' + (authStatusText[realnameStatus] || '未提交')">
      <template #value>
        <van-tag :type="authStatusTag[realnameStatus] || 'default'">
          {{ authStatusText[realnameStatus] || '未提交' }}
        </van-tag>
      </template>
      <van-cell v-if="realname && realname.rejectReason" title="驳回原因" :value="realname.rejectReason" />
    </van-cell-group>

    <van-cell-group inset v-if="canEditRealname">
      <van-field v-model="realnameForm.realName" label="真实姓名" placeholder="与身份证一致" />
      <van-field v-model="realnameForm.idCardNo" label="身份证号" placeholder="18 位" maxlength="18" />
      <van-cell title="身份证正面" center>
        <template #value>
          <ImageUploader v-model="realnameForm.idCardFront" biz="realname" label="身份证正面" />
        </template>
      </van-cell>
      <van-cell title="身份证反面" center>
        <template #value>
          <ImageUploader v-model="realnameForm.idCardBack" biz="realname" label="身份证反面" />
        </template>
      </van-cell>
    </van-cell-group>

    <div class="submit-area" v-if="canEditRealname">
      <van-button block type="primary" :loading="submitting" @click="submitRealname">
        {{ realnameStatus === 'REJECTED' ? '重新提交实名认证' : '提交实名认证' }}
      </van-button>
    </div>

    <!-- 驾照认证 -->
    <van-cell-group inset :title="'驾照认证 · ' + (authStatusText[licenseStatus] || '未提交')">
      <template #value>
        <van-tag :type="authStatusTag[licenseStatus] || 'default'">
          {{ authStatusText[licenseStatus] || '未提交' }}
        </van-tag>
      </template>
      <van-cell v-if="license && license.rejectReason" title="驳回原因" :value="license.rejectReason" />
    </van-cell-group>

    <van-cell-group inset v-if="canEditLicense">
      <van-field v-model="licenseForm.licenseNo" label="驾驶证号" placeholder="必填" />
      <van-field v-model="licenseForm.licenseClass" label="准驾车型" placeholder="如 C1" />
      <van-field
        v-model="licenseForm.issueDate"
        label="初次领证"
        placeholder="如 2018-06-01"
        readonly
        is-link
        @click="showIssuePicker = true"
      />
      <van-field
        v-model="licenseForm.expireDate"
        label="有效期至"
        placeholder="如 2028-06-01"
        readonly
        is-link
        @click="showExpirePicker = true"
      />
      <van-cell title="驾驶证正页" center>
        <template #value>
          <ImageUploader v-model="licenseForm.licenseFront" biz="license" label="驾驶证正页" />
        </template>
      </van-cell>
      <van-cell title="驾驶证副页" center>
        <template #value>
          <ImageUploader v-model="licenseForm.licenseBack" biz="license" label="驾驶证副页" />
        </template>
      </van-cell>
    </van-cell-group>

    <div class="submit-area" v-if="canEditLicense">
      <van-button block type="primary" :loading="submitting" @click="submitLicense">
        {{ licenseStatus === 'REJECTED' ? '重新提交驾照认证' : '提交驾照认证' }}
      </van-button>
    </div>

    <van-popup v-model:show="showIssuePicker" position="bottom">
      <van-date-picker
        v-model="issueDateValue"
        title="初次领证日期"
        @confirm="onIssueConfirm"
        @cancel="showIssuePicker = false"
      />
    </van-popup>

    <van-popup v-model:show="showExpirePicker" position="bottom">
      <van-date-picker
        v-model="expireDateValue"
        title="有效期至"
        @confirm="onExpireConfirm"
        @cancel="showExpirePicker = false"
      />
    </van-popup>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { showToast } from 'vant'
import ImageUploader from '@shared/components/ImageUploader.vue'
import { verificationApi } from '@shared/api'
import { authStatusText, authStatusTag } from '@shared/utils/dict'

const summary = reactive({
  realname: null,
  license: null,
  realnamePassed: false,
  licensePassed: false,
  rentReady: false
})

const submitting = ref(false)
const showIssuePicker = ref(false)
const showExpirePicker = ref(false)

const realnameForm = reactive({
  realName: '',
  idCardNo: '',
  idCardFront: '',
  idCardBack: ''
})

const licenseForm = reactive({
  licenseNo: '',
  licenseClass: '',
  issueDate: '',
  expireDate: '',
  licenseFront: '',
  licenseBack: ''
})

const today = new Date()
const pad = (n) => String(n).padStart(2, '0')
const todayParts = [String(today.getFullYear()), pad(today.getMonth() + 1), pad(today.getDate())]
const issueDateValue = ref([...todayParts])
const expireDateValue = ref([String(today.getFullYear() + 6), ...todayParts.slice(1)])

const realname = computed(() => summary.realname)
const license = computed(() => summary.license)
const realnameStatus = computed(() => summary.realname?.status || '')
const licenseStatus = computed(() => summary.license?.status || '')

// 待审核和已通过时不再重复提交；被驳回或没提交过才显示表单
const canEditRealname = computed(() => realnameStatus.value !== 'PENDING' && realnameStatus.value !== 'APPROVED')
const canEditLicense = computed(() => licenseStatus.value !== 'PENDING' && licenseStatus.value !== 'APPROVED')

async function load() {
  try {
    const data = await verificationApi.me()
    summary.realname = data.realname
    summary.license = data.license
    summary.realnamePassed = data.realnamePassed
    summary.licensePassed = data.licensePassed
    summary.rentReady = data.rentReady

    if (data.realname) {
      realnameForm.realName = data.realname.realName || ''
      realnameForm.idCardNo = data.realname.idCardNo || ''
      realnameForm.idCardFront = data.realname.idCardFront || ''
      realnameForm.idCardBack = data.realname.idCardBack || ''
    }
    if (data.license) {
      licenseForm.licenseNo = data.license.licenseNo || ''
      licenseForm.licenseClass = data.license.licenseClass || ''
      licenseForm.issueDate = data.license.issueDate || ''
      licenseForm.expireDate = data.license.expireDate || ''
      licenseForm.licenseFront = data.license.licenseFront || ''
      licenseForm.licenseBack = data.license.licenseBack || ''
    }
  } catch (e) {
    showToast(e.message || '加载认证信息失败')
  }
}

function onIssueConfirm({ selectedValues }) {
  licenseForm.issueDate = selectedValues.join('-')
  showIssuePicker.value = false
}

function onExpireConfirm({ selectedValues }) {
  licenseForm.expireDate = selectedValues.join('-')
  showExpirePicker.value = false
}

async function submitRealname() {
  if (!realnameForm.realName || !realnameForm.idCardNo) {
    showToast('请填写真实姓名和身份证号')
    return
  }
  submitting.value = true
  try {
    await verificationApi.submitRealname({ ...realnameForm })
    showToast({ type: 'success', message: '已提交，等待审核' })
    await load()
  } catch (e) {
    showToast(e.message || '提交失败')
  } finally {
    submitting.value = false
  }
}

async function submitLicense() {
  if (!licenseForm.licenseNo) {
    showToast('请填写驾驶证号')
    return
  }
  submitting.value = true
  try {
    await verificationApi.submitLicense({
      ...licenseForm,
      issueDate: licenseForm.issueDate || null,
      expireDate: licenseForm.expireDate || null
    })
    showToast({ type: 'success', message: '已提交，等待审核' })
    await load()
  } catch (e) {
    showToast(e.message || '提交失败')
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.ready-banner {
  display: flex;
  align-items: center;
  gap: 12px;
}

.ready-banner.ok {
  background: #eefaf1;
  color: #16794b;
}

.ready-banner.warn {
  background: #fff8ec;
  color: #9a6300;
}

.ready-title {
  font-size: 15px;
  font-weight: 600;
}

.submit-area {
  margin: 12px 16px 4px;
}
</style>
