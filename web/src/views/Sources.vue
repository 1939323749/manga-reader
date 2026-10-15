<script setup>
import { computed, onMounted, ref } from 'vue'
import { api } from '../api.js'
import { compareLang, langName } from '../lang.js'

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

// One block per extension. Multi-language extensions (e.g. MangaDex) list their
// languages compactly instead of repeating the same name dozens of times.
const extensions = computed(() => {
  const q = filter.value.trim().toLowerCase()
  const map = new Map()
  for (const s of sources.value) {
    if (q && !s.name.toLowerCase().includes(q) && !s.extensionName.toLowerCase().includes(q)) continue
    if (!map.has(s.pkgName)) map.set(s.pkgName, { pkgName: s.pkgName, name: s.extensionName, iconUrl: s.iconUrl, sources: [] })
    map.get(s.pkgName).sources.push(s)
  }
  for (const ext of map.values()) {
    ext.sources.sort((a, b) => compareLang(a.lang, b.lang) || a.name.localeCompare(b.name))
    // Several sources in one language (a "factory" extension with mirrors) need their own names
    const names = new Set(ext.sources.map((s) => s.name))
    ext.namedSources = names.size > 1
  }
  return [...map.values()].sort((a, b) => a.name.localeCompare(b.name))
})

function label(ext, s) {
  if (ext.sources.length === 1) return null
  return ext.namedSources ? `${s.name}（${langName(s.lang)}）` : langName(s.lang)
}
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
    <ul class="list">
      <li v-for="ext in extensions" :key="ext.pkgName" class="ext">
        <!-- single-source extension: the whole row is the link -->
        <RouterLink
          v-if="ext.sources.length === 1"
          :to="{ name: 'browse', params: { sourceId: ext.sources[0].id } }"
          class="row link"
        >
          <img v-if="ext.iconUrl" :src="ext.iconUrl" alt="" class="icon" loading="lazy" />
          <span v-else class="icon halftone"></span>
          <span class="name">{{ ext.sources[0].name }}</span>
          <span class="lang muted">{{ langName(ext.sources[0].lang) }}</span>
        </RouterLink>

        <template v-else>
          <div class="row">
            <img v-if="ext.iconUrl" :src="ext.iconUrl" alt="" class="icon" loading="lazy" />
            <span v-else class="icon halftone"></span>
            <span class="name">{{ ext.name }}</span>
            <span class="lang muted">{{ ext.sources.length }} 个来源</span>
          </div>
          <ul class="variants">
            <li v-for="s in ext.sources" :key="s.id">
              <RouterLink :to="{ name: 'browse', params: { sourceId: s.id } }" class="variant">
                {{ label(ext, s) }}
              </RouterLink>
            </li>
          </ul>
        </template>
      </li>
    </ul>
    <p v-if="!extensions.length" class="empty">没有名字包含“{{ filter }}”的来源。</p>
  </template>
</template>

<style scoped>
.head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 20px;
  max-width: 820px;
}

.placeholder {
  height: 160px;
  max-width: 820px;
  border: var(--panel-border) solid var(--ink);
}

.list {
  list-style: none;
  margin: 0;
  padding: 0;
  max-width: 820px;
  border-top: var(--panel-border) solid var(--ink);
}

.ext {
  border-bottom: 1px solid var(--tone);
  padding: 4px 0;
}

.row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 4px;
  color: var(--ink);
}

.row.link:hover {
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

.lang {
  margin-left: auto;
  font-size: var(--step--1);
}

.variants {
  list-style: none;
  margin: 0 0 8px 48px;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.variant {
  display: inline-block;
  border: 1px solid var(--ink);
  color: var(--ink);
  padding: 1px 10px;
  font-size: var(--step--1);
}

.variant:hover {
  background: var(--ink);
  color: var(--paper);
  text-decoration: none;
}

@media (max-width: 480px) {
  .variants {
    margin-left: 0;
  }
}
</style>
