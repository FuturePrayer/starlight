<template>
  <ul class="outline-list" v-if="items.length">
    <li
      v-for="(item, i) in items"
      :key="i"
      class="outline-item"
      :style="{ paddingLeft: (item.level - 2) * 16 + 'px' }"
    >
      <a
        :href="'#' + item.anchor"
        :class="['outline-link', { active: item.anchor === activeAnchor }]"
        @click.prevent="emit('select', item)"
      >
        {{ item.title }}
      </a>
    </li>
  </ul>
  <div v-else class="outline-empty">暂无大纲（需要 ## 及以上标题）</div>
</template>

<script setup>
import { computed } from 'vue'
import { parseOutline } from '@/utils/markdown'

const props = defineProps({
  markdown: { type: String, default: '' },
  activeAnchor: { type: String, default: '' }
})

const emit = defineEmits(['select'])

const items = computed(() => parseOutline(props.markdown))
</script>

<style scoped>
.outline-list { list-style: none; padding: 0; margin: 0; }
.outline-item { margin: 2px 0; }
.outline-link {
  display: block;
  padding: 4px 10px;
  border-radius: var(--sl-radius);
  font-size: 12px;
  color: var(--sl-text-secondary);
  text-decoration: none;
  transition: background 0.1s, color 0.1s;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.outline-link:hover { background: var(--sl-hover-bg); color: var(--sl-text); text-decoration: none; }
.outline-link.active {
  background: var(--sl-selection);
  color: var(--sl-primary);
}
.outline-empty {
  font-size: 12px;
  color: var(--sl-text-tertiary);
  text-align: center;
  padding: 24px 12px;
}
</style>

