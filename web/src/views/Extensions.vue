<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { api } from '../api.js'
import { compareLang, langName } from '../lang.js'

const all = ref([])
const loading = ref(true)
const refreshing = ref(false)
const error = ref('')
const query = ref('')
const lang = ref('zh')
const showNsfw = ref(false)
const busy = reactive({}) // pkgName -> 'install' | 'uninstall' | 'update'
const rowError = reactive({}) // pkgName -> message

async function load(refresh = false) {
  error.value = ''
  if (refresh) refreshing.value = true
  else loading.value = true
  try {
    all.value = await api.repoExtensions(refresh)
    if (!all.value.some((e) => e.lang === lang.value)) lang.value = ''
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

onMounted(() => load())

const langs = computed(() => [...new Set(all.value.map((e) => e.lang))].sort(compareLang))

const visible = computed(() => {
  const q = query.value.trim().toLowerCase()
  return all.value.filter(
    (e) =>
      (showNsfw.value || !e.nsfw || e.installed) &&
      (!lang.value || e.lang === lang.value || e.installed) &&
      (!q || e.name.toLowerCase().includes(q) || e.pkgName.includes(q)),
  )
})

const installed = computed(() => visible.value.filter((e) => e.installed))
const available = computed(() => visible.value.filter((e) => !e.installed))

async function run(ext, action) {
  busy[ext.pkgName] = action
  delete rowError[ext.pkgName]
  try {
    if (action === 'uninstall') {
      await api.uninstall(ext.pkgName)
      Object.assign(ext, { installed: false, installedVersion: null, hasUpdate: false })
    } else {
      const res = await api.install(ext.pkgName)
      Object.assign(ext, { installed: true, installedVersion: res.versionName, hasUpdate: false })
    }
  } catch (e) {
    rowError[ext.pkgName] = e.message
  } finally {
    delete busy[ext.pkgName]
  }
}

const busyLabel = { install: '安装中…', update: '更新中…', uninstall: '卸载中…' }
</script>

<template>
  <header class="head">
    <h1>扩展</h1>
    <button class="btn" :disabled="refreshing || loading" @click="load(true)">
      {{ refreshing ? '正在刷新…' : '刷新列表' }}
    </button>
  </header>

  <div class="filters">
    <input v-model="query" type="search" placeholder="按名称搜索" aria-label="按名称搜索扩展" />
    <select v-model="lang" aria-label="语言">
      <option value="">全部语言</option>
      <option v-for="l in langs" :key="l" :value="l">{{ langName(l) }}</option>
    </select>
    <label class="check">
      <input v-model="showNsfw" type="checkbox" />
      显示成人内容
    </label>
  </div>

  <div v-if="error" class="error-box">
    无法读取扩展仓库：{{ error }}
    <button class="btn" @click="load()">重试</button>
  </div>

  <div v-else-if="loading" class="halftone loading placeholder" aria-label="加载中"></div>

  <template v-else>
    <section v-if="installed.length" class="block">
      <h2>已安装</h2>
      <ul class="list">
        <li v-for="e in installed" :key="e.pkgName" class="row">
          <img :src="e.iconUrl" alt="" class="icon" loading="lazy" />
          <div class="info">
            <span class="name">{{ e.name }}</span>
            <span class="meta muted">
              {{ langName(e.lang) }}，版本 {{ e.installedVersion }}
              <template v-if="e.hasUpdate">，可更新到 {{ e.versionName }}</template>
            </span>
            <span v-if="rowError[e.pkgName]" class="row-error">{{ rowError[e.pkgName] }}</span>
          </div>
          <div class="actions">
            <span v-if="busy[e.pkgName]" class="muted">{{ busyLabel[busy[e.pkgName]] }}</span>
            <template v-else>
              <button v-if="e.hasUpdate" class="btn primary" @click="run(e, 'update')">更新</button>
              <button class="btn danger" @click="run(e, 'uninstall')">卸载</button>
            </template>
          </div>
        </li>
      </ul>
    </section>

    <section class="block">
      <h2>可安装 <span class="count muted">{{ available.length }}</span></h2>
      <ul v-if="available.length" class="list">
        <li v-for="e in available" :key="e.pkgName" class="row">
          <img :src="e.iconUrl" alt="" class="icon" loading="lazy" />
          <div class="info">
            <span class="name">
              {{ e.name }}
              <span v-if="e.nsfw" class="tag">成人</span>
            </span>
            <span class="meta muted">{{ langName(e.lang) }}，版本 {{ e.versionName }}，{{ e.sourceCount }} 个来源</span>
            <span v-if="rowError[e.pkgName]" class="row-error">{{ rowError[e.pkgName] }}</span>
          </div>
          <div class="actions">
            <span v-if="busy[e.pkgName]" class="muted">{{ busyLabel[busy[e.pkgName]] }}</span>
            <button v-else class="btn" @click="run(e, 'install')">安装</button>
          </div>
        </li>
      </ul>
      <p v-else class="empty">没有符合条件的扩展，试试换个语言或关键词。</p>
    </section>
  </template>
</template>

<style scoped>
.head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
  max-width: 820px;
}

.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-bottom: 24px;
}

.filters input[type='search'] {
  flex: 1 1 220px;
  max-width: 360px;
}

.check {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--step--1);
}

.placeholder {
  height: 200px;
  border: var(--panel-border) solid var(--ink);
}

.block + .block {
  margin-top: 32px;
}

.block h2 {
  max-width: 820px;
  font-size: var(--step-1);
  padding-bottom: 6px;
  border-bottom: var(--panel-border) solid var(--ink);
}

.count {
  font-family: var(--font-body);
  font-weight: 400;
  font-size: var(--step--1);
  margin-left: 6px;
}

.list {
  list-style: none;
  margin: 0;
  padding: 0;
  max-width: 820px;
}

.row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 4px;
  border-bottom: 1px solid var(--tone);
}

.icon {
  width: 40px;
  height: 40px;
  flex: none;
  border: 2px solid var(--ink);
  background: var(--tone);
  object-fit: cover;
}

.info {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.name {
  font-weight: 600;
  overflow-wrap: anywhere;
}

.meta {
  font-size: var(--step--1);
}

.tag {
  font-size: 11px;
  font-weight: 400;
  border: 1px solid var(--danger);
  color: var(--danger);
  padding: 0 4px;
  margin-left: 6px;
  vertical-align: 2px;
}

.row-error {
  color: var(--danger);
  font-size: var(--step--1);
  overflow-wrap: anywhere;
}

.actions {
  display: flex;
  gap: 8px;
  flex: none;
}
</style>
