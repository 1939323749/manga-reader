<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api.js'
import FilterPanel from '../components/FilterPanel.vue'
import MangaGrid from '../components/MangaGrid.vue'
import { listCache } from '../store.js'

const props = defineProps({ sourceId: { type: String, required: true } })
const route = useRoute()
const router = useRouter()

const source = ref(null)
const sourceError = ref('')

// Applied filters live in the URL (?f=) as changes against the defaults, so back/forward works
const filterParam = computed(() => String(route.query.f || ''))
const appliedFilters = computed(() => {
  try {
    const f = JSON.parse(filterParam.value || '[]')
    return Array.isArray(f) ? f : []
  } catch {
    return []
  }
})
const mode = computed(() =>
  route.query.q || appliedFilters.value.length ? 'search' : route.query.tab === 'latest' ? 'latest' : 'popular',
)
const query = computed(() => String(route.query.q || ''))
const searchInput = ref(query.value)
const showFilters = ref(false)

const mangas = ref([])
const page = ref(0)
const hasNext = ref(true)
const loading = ref(false)
const error = ref('')

const cacheKey = computed(() => `${props.sourceId}|${mode.value}|${query.value}|${filterParam.value}`)

async function loadSource() {
  sourceError.value = ''
  try {
    source.value = await api.source(props.sourceId)
    document.title = `${source.value.name} | 漫画阅读`
  } catch (e) {
    sourceError.value = e.message
  }
}

function reset() {
  const cached = listCache.get(cacheKey.value)
  if (cached) {
    mangas.value = cached.mangas
    page.value = cached.page
    hasNext.value = cached.hasNext
    error.value = ''
    return
  }
  mangas.value = []
  page.value = 0
  hasNext.value = true
  error.value = ''
  loadMore()
}

async function loadMore() {
  if (loading.value || !hasNext.value) return
  loading.value = true
  error.value = ''
  const key = cacheKey.value
  const next = page.value + 1
  try {
    const res =
      mode.value === 'search'
        ? appliedFilters.value.length
          ? await api.searchFiltered(props.sourceId, query.value, next, appliedFilters.value)
          : await api.search(props.sourceId, query.value, next)
        : mode.value === 'latest'
          ? await api.latest(props.sourceId, next)
          : await api.popular(props.sourceId, next)
    if (key !== cacheKey.value) return // user switched tabs meanwhile
    const seen = new Set(mangas.value.map((m) => m.url))
    mangas.value = [...mangas.value, ...res.mangas.filter((m) => !seen.has(m.url))]
    page.value = next
    hasNext.value = res.hasNextPage && res.mangas.length > 0
    listCache.set(key, { mangas: mangas.value, page: page.value, hasNext: hasNext.value })
  } catch (e) {
    if (key === cacheKey.value) error.value = e.message
  } finally {
    loading.value = false
    await nextTick()
    observe()
  }
}

function setTab(tab) {
  router.replace({ query: tab === 'latest' ? { tab } : {} })
  searchInput.value = ''
}

function searchQuery(q, f) {
  return { ...(q ? { q } : {}), ...(f ? { f } : {}) }
}

function submitSearch() {
  router.replace({ query: searchQuery(searchInput.value.trim(), filterParam.value) })
}

function applyFilters(changes) {
  showFilters.value = false
  router.replace({ query: searchQuery(query.value, changes.length ? JSON.stringify(changes) : '') })
}

// Infinite scroll: load the next page when the sentinel comes into view
const sentinel = ref(null)
let observer = null

function observe() {
  if (!sentinel.value || observer) return
  observer = new IntersectionObserver(
    (entries) => {
      if (entries.some((e) => e.isIntersecting)) loadMore()
    },
    { rootMargin: '600px 0px' },
  )
  observer.observe(sentinel.value)
}

onMounted(() => {
  loadSource()
  reset()
})

onBeforeUnmount(() => observer?.disconnect())

watch(cacheKey, reset)
watch(() => props.sourceId, loadSource)
watch(query, (q) => (searchInput.value = q))
</script>

<template>
  <header class="head">
    <RouterLink to="/" class="back">返回来源</RouterLink>
    <div class="title-row">
      <h1>{{ source?.name || '…' }}</h1>
      <RouterLink
        v-if="source?.configurable"
        :to="{ name: 'source-settings', params: { sourceId } }"
        class="btn"
      >
        来源设置
      </RouterLink>
    </div>
  </header>

  <div v-if="sourceError" class="error-box">无法加载这个来源：{{ sourceError }}</div>

  <div class="toolbar">
    <div class="tabs" role="tablist">
      <button
        role="tab"
        class="tab"
        :class="{ active: mode === 'popular' }"
        :aria-selected="mode === 'popular'"
        @click="setTab('popular')"
      >
        热门
      </button>
      <button
        v-if="source?.supportsLatest !== false"
        role="tab"
        class="tab"
        :class="{ active: mode === 'latest' }"
        :aria-selected="mode === 'latest'"
        @click="setTab('latest')"
      >
        最新
      </button>
    </div>
    <form class="search" role="search" @submit.prevent="submitSearch">
      <input v-model="searchInput" type="search" placeholder="在这个来源中搜索" aria-label="搜索漫画" />
      <button class="btn" type="submit">搜索</button>
      <button
        class="btn"
        :class="{ primary: appliedFilters.length }"
        type="button"
        :aria-expanded="showFilters"
        @click="showFilters = !showFilters"
      >
        筛选{{ appliedFilters.length ? `（${appliedFilters.length}）` : '' }}
      </button>
    </form>
  </div>

  <FilterPanel v-if="showFilters" :source-id="sourceId" :applied="appliedFilters" @apply="applyFilters" />

  <p v-if="mode === 'search'" class="muted result-line">
    <template v-if="query">“{{ query }}”的搜索结果</template>
    <template v-else>筛选结果</template>
    <template v-if="appliedFilters.length">
      · 已应用 {{ appliedFilters.length }} 项筛选
      <button class="link" type="button" @click="applyFilters([])">清除</button>
    </template>
  </p>

  <MangaGrid v-if="mangas.length" :source-id="sourceId" :mangas="mangas" />

  <div v-if="error" class="error-box">
    加载失败：{{ error }}
    <button class="btn" @click="loadMore">重试</button>
  </div>

  <div v-else-if="loading" class="halftone loading placeholder" :class="{ first: !mangas.length }" aria-label="加载中"></div>

  <p v-else-if="!mangas.length && !hasNext" class="empty">
    {{ mode === 'search' ? '没有搜到结果，换个关键词试试。' : '这个来源暂时没有返回任何漫画。' }}
  </p>

  <p v-else-if="!hasNext" class="end muted">已经到底了</p>

  <div ref="sentinel" class="sentinel" aria-hidden="true"></div>
</template>

<style scoped>
.head {
  margin-bottom: 18px;
}

.title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}

.link {
  border: 0;
  background: none;
  color: var(--cobalt);
  padding: 0;
  font-size: inherit;
}

.back {
  font-size: var(--step--1);
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 20px;
}

.tabs {
  display: flex;
  border: 2px solid var(--ink);
}

.tab {
  border: 0;
  background: var(--paper);
  color: var(--ink);
  padding: 6px 18px;
}

.tab + .tab {
  border-left: 2px solid var(--ink);
}

.tab.active {
  background: var(--ink);
  color: var(--paper);
}

.search {
  display: flex;
  gap: 8px;
  flex: 1 1 320px;
  max-width: 500px;
}

.search input {
  flex: 1;
}

.result-line {
  margin: -8px 0 14px;
}

.placeholder {
  height: 80px;
  margin-top: var(--gutter);
  border: var(--panel-border) solid var(--ink);
}

.placeholder.first {
  height: 320px;
}

.end {
  text-align: center;
  margin: 28px 0 0;
  font-size: var(--step--1);
}

.sentinel {
  height: 1px;
}
</style>
