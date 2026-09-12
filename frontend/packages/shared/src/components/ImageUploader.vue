<template>
  <div class="uploader">
    <div class="box" @click="pick">
      <img v-if="modelValue" :src="modelValue" alt="" />
      <div v-else class="placeholder">
        <van-icon name="photograph" size="22" />
        <span>{{ label }}</span>
      </div>
    </div>
    <van-button v-if="modelValue" size="mini" plain @click.stop="clear">移除</van-button>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { showToast } from 'vant'
import uni from '@shared/utils/uni'
import { fileApi } from '@shared/api'

const props = defineProps({
  modelValue: { type: String, default: '' },
  /** 业务目录：realname / license / avatar / car */
  biz: { type: String, default: 'other' },
  label: { type: String, default: '上传图片' }
})

const emit = defineEmits(['update:modelValue'])
const uploading = ref(false)

async function pick() {
  if (uploading.value) return
  let chosen
  try {
    chosen = await uni.chooseImage({ count: 1 })
  } catch (e) {
    return // 用户取消
  }

  const file = chosen.tempFiles?.[0]?.file
  if (!file) return
  if (file.size > 5 * 1024 * 1024) {
    showToast('图片不能超过 5MB')
    return
  }

  uploading.value = true
  try {
    const vo = await fileApi.upload(file, props.biz)
    emit('update:modelValue', vo.url)
    showToast({ type: 'success', message: '上传成功' })
  } catch (e) {
    showToast(e.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

function clear() {
  emit('update:modelValue', '')
}
</script>

<style scoped>
.uploader {
  display: flex;
  align-items: center;
  gap: 8px;
}

.box {
  width: 84px;
  height: 56px;
  border-radius: 6px;
  border: 1px dashed #d8dde6;
  background: #fafbfd;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  cursor: pointer;
}

.box img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  color: #96a0b0;
  font-size: 11px;
}
</style>
