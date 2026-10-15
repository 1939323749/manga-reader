<script setup>
import { computed, onMounted, ref } from 'vue'
import { api } from '../api.js'
import { langName } from '../lang.js'

const sources = ref([])
const loading = ref(true)
const error = ref('')
const filter = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    sources.value = await api.sources()
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

onMounted(load)

const groups = computed(() => {
  const q = filter.value.trim().toLowerCase()
  const map = new Map()
  for (const s of sources.value) {
    if (q && !s.name.toLowerCase().includes(q)) continue
    if (!map.has(s.lang)) map.set(s.lang, [])
    map.get(s.lang).push(s)
  }
  return [...map.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([lang, list]) => ({ lang, list: list.sort((a, b) => a.name.localeCompare(b.name)) }))
})
</script>

<template>
  <header class="head">
    <h1>来源</h1>
    <input
      v-if="sources.length > 8"
      v-model="filter"
      type="search"
      placeholder="筛选来源"
      aria-label="筛选来源"
    />
  </header>

  <div v-if="error" class="error-box">
    无法读取来源列表：{{ error }}
    <button class="btn" @click="load">重试</button>
  </div>

  <div v-else-if="loading" class="halftone loading placeholder" aria-label="加载中"></div>

  <div v-else-if="!sources.length" class="empty">
    <p>还没有安装扩展。每个扩展提供一个或多个漫画来源。</p>
    <RouterLink to="/extensions" class="btn primary">去安装扩展</RouterLink>
  </div>

  <template v-else>
    <section v-for="g in groups" :key="g.lang" class="group">
      <h2 class="lang">{{ langName(g.lang) }}</h2>
      <ul class="list">
        <li v-for="s in g.list" :key="s.id">
          <RouterLink :to="{ name: 'browse', params: { sourceId: s.id } }" class="row">
            <img v-if="s.iconUrl" :src="s.iconUrl" alt="" class="icon" loading="lazy" />
            <span v-else class="icon halftone"></span>
            <span class="name">{{ s.name }}</span>
            <span v-if="s.name !== s.extensionName" class="ext muted">{{ s.extensionName }}</span>
          </RouterLink>
        </li>
      </ul>
    </section>
    <p v-if="!groups.length" class="empty">没有名字包含“{{ filter }}”的来源。</p>
  </template>
</template>

<style scoped>
.head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 24px;
}

.placeholder {
  height: 160px;
  border: var(--panel-border) solid var(--ink);
}

.group + .group {
  margin-top: 28px;
}

.lang {
  font-size: var(--step-1);
  padding-bottom: 6px;
  border-bottom: var(--panel-border) solid var(--ink);
}

.list {
  list-style: none;
  margin: 0;
  padding: 0;
  max-width: 720px;
}

.row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 4px;
  color: var(--ink);
  border-bottom: 1px solid var(--tone);
}

.row:hover {
  text-decoration: none;
  background: var(--tone);
}

.icon {
  width: 32px;
  height: 32px;
  flex: none;
  border: 2px solid var(--ink);
  object-fit: cover;
}

.name {
  font-weight: 600;
}

.ext {
  margin-left: auto;
  font-size: var(--step--1);
}
</style>
