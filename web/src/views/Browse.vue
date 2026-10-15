<script>
// Lists survive navigating to a manga and back (keyed by source + mode + query)
const listCache = new Map()
</script>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api.js'
import MangaGrid from '../components/MangaGrid.vue'

const props = defineProps({ sourceId: { type: String, required: true } })
const route = useRoute()
const router = useRouter()

const source = ref(null)
const sourceError = ref('')

const mode = computed(() => (route.query.q ? 'search' : route.query.tab === 'latest' ? 'latest' : 'popular'))
const query = computed(() => String(route.query.q || ''))
const searchInput = ref(query.value)

const mangas = ref([])
const page = ref(0)
const hasNext = ref(true)
const loading = ref(false)
const error = ref('')

const cacheKey = computed(() => `${props.sourceId}|${mode.value}|${query.value}`)

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
        ? await api.search(props.sourceId, query.value, next)
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

function submitSearch() {
  const q = searchInput.value.trim()
  router.replace({ query: q ? { q } : {} })
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
    <h1>{{ source?.name || '…' }}</h1>
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
    </form>
  </div>

  <p v-if="mode === 'search'" class="muted result-line">“{{ query }}”的搜索结果</p>

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
  flex: 1 1 260px;
  max-width: 420px;
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
