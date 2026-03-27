<template>
  <PopupLayer title="创建分类" width="min(440px, calc(100vw - 32px))" @close="$emit('close')">
    <div class="form-field">
      <label class="sl-label">分类名称</label>
      <input v-model="name" class="sl-input" placeholder="例如：技术随笔" @keyup.enter="handleCreate" />
    </div>
    <div class="form-field category-form-field">
      <label class="sl-label">父分类</label>
      <select v-model="parentId" class="sl-select">
        <option value="">无（顶级分类）</option>
        <option v-for="opt in options" :key="opt.id" :value="opt.id">{{ opt.label }}</option>
      </select>
    </div>
    <button class="sl-btn sl-btn--primary category-submit-btn" @click="handleCreate">保存分类</button>
  </PopupLayer>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useNoteStore } from '@/stores/note'
import { useToastStore } from '@/stores/toast'
import PopupLayer from '@/components/PopupLayer.vue'

const props = defineProps({ treeItems: { type: Array, default: () => [] } })
const emit = defineEmits(['close', 'created'])
const noteStore = useNoteStore()
const toast = useToastStore()

const name = ref('')
const parentId = ref('')

const options = computed(() => {
  const result = []
  function walk(items, prefix = '') {
    for (const item of items || []) {
      if (item.type === 'category') {
        result.push({ id: item.id, label: prefix + item.name })
        if (item.children?.length) walk(item.children, prefix + '— ')
      }
    }
  }
  walk(props.treeItems)
  return result
})

async function handleCreate() {
  if (!name.value.trim()) { toast.error('请输入分类名称'); return }
  try {
    await noteStore.createCategory(name.value.trim(), parentId.value || null)
    emit('created')
  } catch (err) {
    toast.error(err.message)
  }
}
</script>

<style scoped>
.category-form-field { margin-top: 12px; }
.category-submit-btn { width: 100%; margin-top: 18px; }
</style>

