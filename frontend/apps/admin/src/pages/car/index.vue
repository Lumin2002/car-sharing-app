<template>
  <div class="page">
    <van-search v-model="keyword" placeholder="搜索品牌 / 型号 / 车牌" @search="reload" />

    <van-dropdown-menu>
      <van-dropdown-item v-model="status" :options="statusOptions" @change="reload" />
    </van-dropdown-menu>

    <div class="add-bar" v-if="auth.canManage">
      <van-button block type="primary" icon="plus" @click="openForm()">新增车辆</van-button>
    </div>

    <van-empty v-if="!loading && cars.length === 0" image="search" description="没有车辆" />

    <div v-for="car in cars" :key="car.carId" class="card-block">
      <div class="row-between">
        <div style="font-size: 15px; font-weight: 600">{{ car.brand }} {{ car.model || '' }}</div>
        <van-tag :type="carStatusTag[car.status]">{{ carStatusText[car.status] }}</van-tag>
      </div>
      <div class="muted mt4">{{ car.plateNo }} · {{ car.vin }}</div>
      <div class="muted mt4">
        ¥{{ car.dailyPrice }}/天 · 押金 ¥{{ car.deposit || 0 }} · {{ car.mileage || 0 }} km
      </div>
      <div class="muted mt4">📍 {{ storeName(car.storeId) }}</div>
      <div class="item-actions" v-if="auth.canManage">
        <van-button size="small" plain type="primary" @click="openAttributes(car)">属性</van-button>
        <van-button size="small" plain type="primary" @click="openForm(car)">编辑</van-button>
        <van-button size="small" plain @click="toggleStatus(car)">切换状态</van-button>
        <van-button size="small" plain type="danger" @click="remove(car)">删除</van-button>
      </div>
      <div class="item-actions" v-else>
        <van-button size="small" plain @click="openAttributes(car)">查看属性</van-button>
      </div>
    </div>

    <div v-if="cars.length" class="load-more">
      <span v-if="finished">共 {{ total }} 辆，没有更多了</span>
      <van-button v-else size="small" plain type="primary" :loading="loading" @click="loadMore">
        加载更多
      </van-button>
    </div>

    <van-dialog
      v-model:show="showForm"
      :title="form.carId ? '编辑车辆' : '新增车辆'"
      :show-confirm-button="false"
      :show-cancel-button="false"
    >
      <div class="form-scroll">
        <van-cell-group inset>
          <van-field v-model="form.vin" label="VIN码" placeholder="必填" />
          <van-field v-model="form.plateNo" label="车牌号" placeholder="必填" />
          <van-field v-model="form.brand" label="品牌" placeholder="必填" />
          <van-field v-model="form.model" label="型号" />
          <van-field v-model="form.color" label="颜色" />
          <van-field v-model.number="form.mileage" type="digit" label="里程(km)" />
          <van-field v-model.number="form.seatNum" type="digit" label="座位数" />
          <van-field v-model.number="form.doorNum" type="digit" label="车门数" />
          <van-field
            :model-value="fuelTypeText[form.fuelType]"
            readonly
            is-link
            label="能源类型"
            @click="openPicker('fuelType', fuelTypeText)"
          />
          <van-field
            :model-value="carTypeText[form.type]"
            readonly
            is-link
            label="车型"
            @click="openPicker('type', carTypeText)"
          />
          <van-field v-model.number="form.dailyPrice" type="number" label="日租金" placeholder="必填" />
          <van-field v-model.number="form.deposit" type="number" label="押金" />
          <van-field
            :model-value="carStatusText[form.status]"
            readonly
            is-link
            label="状态"
            @click="openPicker('status', carStatusText)"
          />
          <van-field label="自动挡">
            <template #input>
              <van-switch v-model="form.automaticGear" size="20" />
            </template>
          </van-field>
          <van-field
            :model-value="storeName(form.storeId)"
            readonly
            is-link
            label="所属门店"
            @click="openStorePicker"
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

    <!-- 车辆属性：查看 / 编辑 -->
    <van-dialog
      v-model:show="attrVisible"
      :title="`车辆属性 · ${attrCar?.brand || ''} ${attrCar?.plateNo || ''}`"
      :show-confirm-button="false"
      :show-cancel-button="false"
    >
      <div class="form-scroll">
        <van-cell-group inset title="动力与续航">
          <van-field
            v-model.number="attrForm.batteryCapacity"
            type="digit"
            label="电池容量"
            placeholder="选填"
            :readonly="!auth.canManage"
          >
            <template #right-icon>kWh</template>
          </van-field>
          <van-field
            v-model.number="attrForm.maxRange"
            type="digit"
            label="最大续航"
            placeholder="选填"
            :readonly="!auth.canManage"
          >
            <template #right-icon>km</template>
          </van-field>
          <van-field label="支持快充" v-if="auth.canManage">
            <template #input>
              <van-switch v-model="attrForm.fastCharge" size="20" />
            </template>
          </van-field>
          <van-cell v-else title="支持快充" :value="attrForm.fastCharge ? '是' : '否'" />
        </van-cell-group>

        <van-cell-group inset title="配置">
          <template v-for="f in attrFeatures" :key="f.key">
            <van-field :label="f.label" v-if="auth.canManage">
              <template #input>
                <van-switch v-model="attrForm[f.key]" size="20" />
              </template>
            </van-field>
            <van-cell v-else :title="f.label" :value="attrForm[f.key] ? '有' : '无'" />
          </template>
        </van-cell-group>

        <van-cell-group inset title="储物空间">
          <van-field
            v-model.number="attrForm.frontTrunkVolume"
            type="digit"
            label="前备箱容积"
            placeholder="选填"
            :readonly="!auth.canManage"
          >
            <template #right-icon>L</template>
          </van-field>
          <van-field
            v-model.number="attrForm.trunkVolume"
            type="digit"
            label="后备箱容积"
            placeholder="选填"
            :readonly="!auth.canManage"
          >
            <template #right-icon>L</template>
          </van-field>
        </van-cell-group>

        <div class="muted attr-tip" v-if="!attrHasData">
          该车辆还没有录入属性，用户在车辆详情页看不到「车辆配置」。
        </div>
      </div>
      <div class="dialog-footer">
        <van-button block plain @click="attrVisible = false">
          {{ auth.canManage ? '取消' : '关闭' }}
        </van-button>
        <van-button v-if="auth.canManage" block type="primary" :loading="savingAttr" @click="saveAttributes">
          保存
        </van-button>
      </div>
    </van-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { showToast, showConfirmDialog } from 'vant'
import { carApi, storeApi } from '@shared/api'
import { useAuthStore } from '@shared/store/auth'
import { carStatusText, carStatusTag, carTypeText, fuelTypeText } from '@shared/utils/dict'

const auth = useAuthStore()

const cars = ref([])
const stores = ref([])
const keyword = ref('')
const status = ref('')
const statusOptions = [
  { text: '全部状态', value: '' },
  { text: '空闲', value: 'FREE' },
  { text: '已租出', value: 'RENTED' },
  { text: '维护中', value: 'MAINTENANCE' },
  { text: '已停用', value: 'DISABLED' }
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
  carId: null,
  vin: '',
  plateNo: '',
  brand: '',
  model: '',
  color: '',
  seatNum: 5,
  doorNum: 4,
  mileage: 0,
  fuelType: 'GASOLINE',
  type: 'ECONOMY',
  automaticGear: true,
  dailyPrice: 200,
  deposit: 1000,
  status: 'FREE',
  storeId: null
})
const form = reactive(emptyForm())

/* ---------------- 车辆属性 ---------------- */

const attrFeatures = [
  { key: 'reverseCamera', label: '倒车影像' },
  { key: 'radar', label: '倒车雷达' },
  { key: 'bluetooth', label: '蓝牙' },
  { key: 'airCondition', label: '空调' },
  { key: 'cruiseControl', label: '定速巡航' },
  { key: 'sunroof', label: '天窗' },
  { key: 'leatherSeat', label: '真皮座椅' }
]

const emptyAttributes = () => ({
  batteryCapacity: null,
  fastCharge: false,
  maxRange: null,
  reverseCamera: false,
  radar: false,
  bluetooth: false,
  airCondition: false,
  cruiseControl: false,
  sunroof: false,
  leatherSeat: false,
  frontTrunkVolume: null,
  trunkVolume: null
})

const attrVisible = ref(false)
const attrCar = ref(null)
const attrHasData = ref(false)
const attrForm = reactive(emptyAttributes())
const savingAttr = ref(false)

function openPicker(field, dict) {
  pickerField.value = field
  pickerColumns.value = Object.keys(dict).map((k) => ({ text: dict[k], value: k }))
  pickerVisible.value = true
}

function openStorePicker() {
  if (!stores.value.length) {
    showToast('请先在「店铺」里新增门店')
    return
  }
  pickerField.value = 'storeId'
  pickerColumns.value = stores.value.map((s) => ({ text: s.name, value: s.storeId }))
  pickerVisible.value = true
}

function storeName(storeId) {
  if (storeId == null) return '未分配门店'
  const store = stores.value.find((s) => s.storeId === storeId)
  return store ? store.name : `门店 #${storeId}`
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
    const data = await carApi.page({
      pageNum: pageNum.value,
      pageSize,
      keyword: keyword.value,
      status: status.value
    })
    const records = data.records || []
    cars.value = append ? cars.value.concat(records) : records
    total.value = data.total || 0
    finished.value = cars.value.length >= total.value || records.length < pageSize
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

function openForm(car) {
  Object.assign(form, emptyForm(), car || {})
  showForm.value = true
}

async function save() {
  if (!form.vin || !form.plateNo || !form.brand || !form.dailyPrice) {
    showToast('VIN、车牌、品牌、日租金必填')
    return
  }
  saving.value = true
  const payload = { ...form }
  delete payload.carId
  try {
    if (form.carId) {
      await carApi.update(form.carId, payload)
    } else {
      await carApi.add(payload)
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

async function toggleStatus(car) {
  const next = car.status === 'FREE' ? 'DISABLED' : 'FREE'
  try {
    await carApi.changeStatus(car.carId, next)
    showToast({ type: 'success', message: '已切换为' + carStatusText[next] })
    reload()
  } catch (e) {
    showToast(e.message || '操作失败')
  }
}

async function openAttributes(car) {
  attrCar.value = car
  attrHasData.value = false
  Object.assign(attrForm, emptyAttributes())
  try {
    const data = await carApi.attributes(car.carId)
    if (data) {
      attrHasData.value = true
      // 后端返回 null 的布尔字段统一转成 false，开关组件需要布尔值
      Object.keys(emptyAttributes()).forEach((key) => {
        if (data[key] !== undefined && data[key] !== null) {
          attrForm[key] = data[key]
        }
      })
    }
  } catch (e) {
    // 没有属性记录时接口返回 code=200 data=null，这里一般是网络问题
    showToast(e.message || '读取车辆属性失败')
  }
  attrVisible.value = true
}

async function saveAttributes() {
  if (!attrCar.value) return
  savingAttr.value = true
  try {
    await carApi.saveAttributes(attrCar.value.carId, { ...attrForm })
    showToast({ type: 'success', message: '属性已保存' })
    attrHasData.value = true
    attrVisible.value = false
  } catch (e) {
    showToast(e.message || '保存失败')
  } finally {
    savingAttr.value = false
  }
}

function remove(car) {
  showConfirmDialog({
    title: '删除车辆',
    message: `确定删除 ${car.brand} ${car.model || ''}（${car.plateNo}）吗？`
  })
    .then(() => carApi.remove(car.carId))
    .then(() => {
      showToast({ type: 'success', message: '已删除' })
      reload()
    })
    .catch((e) => {
      if (e && e.message && e.message !== 'cancel') showToast(e.message)
    })
}

onMounted(async () => {
  try {
    stores.value = await storeApi.list()
  } catch (e) {
    stores.value = []
  }
  reload()
})
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

.attr-tip {
  padding: 10px 16px 0;
  line-height: 1.5;
}

</style>
