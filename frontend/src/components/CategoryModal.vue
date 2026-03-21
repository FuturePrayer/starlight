<template>
  <Teleport to="body">
    <div class="modal-backdrop" @click.self="$emit('close')">
      <div class="modal sl-card">
        <div class="modal-header">
          <h3>创建分类</h3>
          <button class="sl-btn sl-btn--ghost sl-btn--sm" @click="$emit('close')">✕</button>
        </div>
        <div class="modal-body">
          <div class="form-field">
            <label class="sl-label">分类名称</label>
            <input v-model="name" class="sl-input" placeholder="例如：技术随笔" @keyup.enter="handleCreate" />
          </div>
          <div class="form-field" style="margin-top:12px">
            <label class="sl-label">父分类</label>
            <select v-model="parentId" class="sl-select">
              <option value="">无（顶级分类）</option>
              <option v-for="opt in options" :key="opt.id" :value="opt.id">{{ opt.label }}</option>
            </select>
          </div>
          <button class="sl-btn sl-btn--primary" style="width:100%;margin-top:18px" @click="handleCreate">保存分类</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useNoteStore } from '@/stores/note'
import { useToastStore } from '@/stores/toast'

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
.modal-backdrop {
  position: fixed; inset: 0;
  background: var(--sl-backdrop);
  display: flex; align-items: center; justify-content: center;
  z-index: 200; padding: 20px;
  animation: sl-fade-in 0.15s ease;
}
.modal {
  width: min(440px, 100%);
  padding: 24px;
  animation: sl-scale-in 0.2s ease;
}
.modal-header {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 20px;
}
.modal-header h3 { font-size: 16px; font-weight: 600; margin: 0; }
</style>

