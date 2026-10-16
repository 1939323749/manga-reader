<script setup>
// The source's own settings (what it builds in setupPreferenceScreen). Every change is saved
// right away; the server then reloads the extension so values read at start-up (e.g. a mirror
// domain) take effect.
import { computed, onMounted, reactive, ref } from 'vue'
import { api } from '../api.js'
import { clearListCache } from '../store.js'

const props = defineProps({ sourceId: { type: String, required: true } })

const source = ref(null)
const prefs = ref([])
const configurable = ref(true)
const loading = ref(true)
const error = ref('')
const saving = ref('') // key being saved
const status = reactive({ key: '', ok: true, text: '' })
const texts = reactive({}) // edit-text drafts by key

const visible = computed(() => prefs.value.filter((p) => p.visible))

function adopt(res) {
  configurable.value = res.configurable
  prefs.value = res.preferences
  for (const p of res.preferences) if (p.type === 'text' && p.key) texts[p.key] = p.value ?? ''
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [s, res] = await Promise.all([api.source(props.sourceId), api.preferences(props.sourceId)])
    source.value = s
    document.title = `${s.name} 设置 | 漫画阅读`
    adopt(res)
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

async function save(p, value) {
  saving.value = p.key
  status.key = p.key
  status.text = ''
  try {
    adopt(await api.setPreference(props.sourceId, p.key, value))
    clearListCache(props.sourceId)
    status.ok = true
    status.text = '已保存'
  } catch (e) {
    status.ok = false
    status.text = e.message
    // show the stored value again
    adopt(await api.preferences(props.sourceId).catch(() => ({ configurable: configurable.value, preferences: prefs.value })))
  } finally {
    saving.value = ''
  }
}

function toggleMulti(p, v, on) {
  const set = new Set(p.value || [])
  if (on) set.add(v)
  else set.delete(v)
  save(p, [...set])
}

function entryLabel(p) {
  const i = (p.entryValues || []).indexOf(p.value)
  return i >= 0 ? p.entries[i] : p.value
}

onMounted(load)
</script>

<template>
  <header class="head">
    <RouterLink :to="{ name: 'browse', params: { sourceId } }" class="back">返回{{ source?.name || '来源' }}</RouterLink>
    <h1>{{ source?.name || '…' }} 设置</h1>
    <p class="muted note">修改后立即保存，来源会重新加载；有些设置（比如镜像域名）要重新打开列表才看得到变化。</p>
  </header>

  <div v-if="error" class="error-box">
    无法读取设置：{{ error }}
    <button class="btn" @click="load">重试</button>
  </div>

  <div v-else-if="loading" class="halftone loading placeholder" aria-label="加载中"></div>

  <p v-else-if="!configurable || !visible.length" class="empty">这个来源没有可以修改的设置。</p>

  <ul v-else class="prefs">
    <li v-for="p in visible" :key="p.key || p.title" class="pref" :class="{ disabled: !p.enabled }">
      <!-- switch / checkbox: the whole row toggles -->
      <label v-if="p.type === 'switch' || p.type === 'checkbox'" class="row">
        <span class="text">
          <span class="title">{{ p.title }}</span>
          <span v-if="p.summary" class="summary muted">{{ p.summary }}</span>
        </span>
        <input
          type="checkbox"
          :class="p.type"
          :checked="!!p.value"
          :disabled="!p.enabled || saving === p.key"
          @change="save(p, $event.target.checked)"
        />
      </label>

      <label v-else-if="p.type === 'list'" class="row">
        <span class="text">
          <span class="title">{{ p.title }}</span>
          <span v-if="p.summary && !p.summary.includes('%s')" class="summary muted">{{ p.summary }}</span>
          <span v-else class="summary muted">当前：{{ entryLabel(p) ?? '未设置' }}</span>
        </span>
        <select :value="p.value" :disabled="!p.enabled || saving === p.key" @change="save(p, $event.target.value)">
          <option v-if="!p.entryValues?.includes(p.value)" :value="p.value" disabled>{{ p.value ?? '未设置' }}</option>
          <option v-for="(v, i) in p.entryValues" :key="v" :value="v">{{ p.entries[i] }}</option>
        </select>
      </label>

      <fieldset v-else-if="p.type === 'multiselect'" class="multi">
        <legend class="title">{{ p.title }}</legend>
        <span v-if="p.summary" class="summary muted">{{ p.summary }}</span>
        <div class="options">
          <label v-for="(v, i) in p.entryValues" :key="v" class="opt">
            <input
              type="checkbox"
              :checked="(p.value || []).includes(v)"
              :disabled="!p.enabled || saving === p.key"
              @change="toggleMulti(p, v, $event.target.checked)"
            />
            {{ p.entries[i] }}
          </label>
        </div>
      </fieldset>

      <form v-else-if="p.type === 'text'" class="edit" @submit.prevent="save(p, texts[p.key])">
        <label :for="`pref-${p.key}`" class="title">{{ p.title }}</label>
        <span v-if="p.summary" class="summary muted">{{ p.summary }}</span>
        <span v-if="p.dialogMessage && p.dialogMessage !== p.summary" class="summary muted">{{ p.dialogMessage }}</span>
        <div class="edit-row">
          <input :id="`pref-${p.key}`" v-model="texts[p.key]" type="text" :disabled="!p.enabled" />
          <button class="btn" type="submit" :disabled="!p.enabled || saving === p.key || texts[p.key] === (p.value ?? '')">
            保存
          </button>
        </div>
      </form>

      <div v-else class="text">
        <span class="title">{{ p.title }}</span>
        <span v-if="p.summary" class="summary muted">{{ p.summary }}</span>
      </div>

      <p v-if="status.key === p.key && status.text" class="status" :class="{ bad: !status.ok }" role="status">
        {{ status.text }}
      </p>
    </li>
  </ul>
</template>

<style scoped>
.head {
  margin-bottom: 18px;
  max-width: 820px;
}

.back {
  font-size: var(--step--1);
}

.note {
  margin: 6px 0 0;
  font-size: var(--step--1);
}

.placeholder {
  height: 200px;
  max-width: 820px;
  border: var(--panel-border) solid var(--ink);
}

.prefs {
  list-style: none;
  margin: 0;
  padding: 0;
  max-width: 820px;
  border-top: var(--panel-border) solid var(--ink);
}

.pref {
  border-bottom: 1px solid var(--tone);
  padding: 12px 4px;
}

.pref.disabled {
  opacity: 0.55;
}

.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.row select {
  max-width: 50%;
}

.text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.title {
  font-weight: 600;
}

.summary {
  font-size: var(--step--1);
  overflow-wrap: anywhere;
  white-space: pre-line;
}

input[type='checkbox'] {
  width: 20px;
  height: 20px;
  flex: none;
  accent-color: var(--cobalt);
}

.multi {
  border: 0;
  margin: 0;
  padding: 0;
}

.multi legend {
  padding: 0;
}

.options {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 4px 14px;
  margin-top: 6px;
}

.opt {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--step--1);
}

.edit {
  display: flex;
  flex-direction: column;
}

.edit-row {
  display: flex;
  gap: 8px;
  margin-top: 6px;
}

.edit-row input {
  flex: 1;
}

.status {
  margin: 6px 0 0;
  font-size: var(--step--1);
  color: var(--cobalt);
}

.status.bad {
  color: var(--danger);
}

@media (max-width: 560px) {
  .row:has(select) {
    flex-direction: column;
    align-items: stretch;
  }

  .row select {
    max-width: none;
  }
}
</style>
