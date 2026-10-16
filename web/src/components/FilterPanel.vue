<script setup>
// Edits a draft of the source's filter list and emits only what differs from the defaults,
// as [{ index, childIndex?, state }] — the shape the search API takes.
import { onMounted, ref, watch } from 'vue'
import { api } from '../api.js'
import FilterItem from './FilterItem.vue'

const props = defineProps({
  sourceId: { type: String, required: true },
  applied: { type: Array, default: () => [] },
})
const emit = defineEmits(['apply'])

const defaults = ref([])
const draft = ref([])
const loading = ref(false)
const error = ref('')

const clone = (v) => JSON.parse(JSON.stringify(v))
const same = (a, b) => JSON.stringify(a ?? null) === JSON.stringify(b ?? null)

function withChanges(changes) {
  const list = clone(defaults.value)
  for (const c of changes) {
    const top = list[c.index]
    const target = c.childIndex == null ? top : top?.filters?.[c.childIndex]
    if (target) target.state = c.state
  }
  return list
}

// Some sources build their filter list from data they fetch later, so reload it each time
async function load() {
  loading.value = true
  error.value = ''
  try {
    defaults.value = await api.filters(props.sourceId)
    draft.value = withChanges(props.applied)
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function changes() {
  const out = []
  draft.value.forEach((f, i) => {
    const d = defaults.value[i]
    if (f.type === 'group') {
      f.filters.forEach((c, j) => {
        if (!same(c.state, d.filters[j]?.state)) out.push({ index: f.index, childIndex: c.index, state: c.state })
      })
    } else if (f.type !== 'header' && f.type !== 'separator' && !same(f.state, d.state)) {
      out.push({ index: f.index, state: f.state })
    }
  })
  return out
}

function reset() {
  draft.value = clone(defaults.value)
}

onMounted(load)
watch(() => props.sourceId, load)
</script>

<template>
  <form class="panel" @submit.prevent="emit('apply', changes())">
    <div v-if="loading" class="halftone loading placeholder" aria-label="加载中"></div>
    <div v-else-if="error" class="error-box">
      无法读取筛选器：{{ error }}
      <button type="button" class="btn" @click="load">重试</button>
    </div>
    <p v-else-if="!draft.length" class="muted none">这个来源没有提供筛选器。</p>
    <template v-else>
      <div class="items">
        <FilterItem v-for="f in draft" :key="f.index" :filter="f" />
      </div>
      <div class="actions">
        <button type="button" class="btn" @click="reset">恢复默认</button>
        <button type="button" class="btn" @click="load">重新载入</button>
        <button type="submit" class="btn primary">应用筛选</button>
      </div>
    </template>
  </form>
</template>

<style scoped>
.panel {
  border: var(--panel-border) solid var(--ink);
  padding: 14px 16px;
  margin-bottom: 20px;
}

.items {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 10px 16px;
  align-items: end;
}

.actions {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}

.placeholder {
  height: 120px;
}

.none {
  margin: 0;
}
</style>
