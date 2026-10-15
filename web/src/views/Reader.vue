<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api.js'
import { lookupChapters, lookupManga, recordRead, rememberChapters, rememberManga } from '../store.js'

const props = defineProps({
  sourceId: { type: String, required: true },
  mangaUrl: { type: String, required: true },
  chapterUrl: { type: String, required: true },
})

const router = useRouter()

const manga = ref(lookupManga(props.sourceId, props.mangaUrl))
const chapters = ref(lookupChapters(props.sourceId, props.mangaUrl) || [])
const pages = ref([])
const loading = ref(true)
const error = ref('')
const barsVisible = ref(true)
const retries = ref({}) // page index -> retry count, used to bust the browser cache
const failed = ref({})
const loaded = ref({})
const sourceName = ref('')

// Sources list chapters newest first
const index = computed(() => chapters.value.findIndex((c) => c.url === props.chapterUrl))
const chapter = computed(() => chapters.value[index.value] || null)
const nextChapter = computed(() => (index.value > 0 ? chapters.value[index.value - 1] : null))
const prevChapter = computed(() =>
  index.value >= 0 && index.value < chapters.value.length - 1 ? chapters.value[index.value + 1] : null,
)

async function ensureChapters() {
  if (chapters.value.some((c) => c.url === props.chapterUrl)) return
  const res = await api.manga(props.sourceId, lookupManga(props.sourceId, props.mangaUrl))
  manga.value = res.manga
  chapters.value = res.chapters
  rememberManga(props.sourceId, res.manga)
  rememberChapters(props.sourceId, props.mangaUrl, res.chapters)
}

async function load() {
  loading.value = true
  error.value = ''
  pages.value = []
  retries.value = {}
  failed.value = {}
  loaded.value = {}
  try {
    await ensureChapters()
    const ch = chapter.value || { url: props.chapterUrl }
    pages.value = await api.pages(props.sourceId, ch)
    if (!pages.value.length) error.value = '这一话没有图片，可能是外部链接或已被删除。'
    document.title = `${ch.name || '阅读'} | ${manga.value.title || '漫画阅读'}`
    if (chapter.value) {
      recordRead({ sourceId: props.sourceId, sourceName: sourceName.value, manga: manga.value, chapter: chapter.value })
    }
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  api.source(props.sourceId).then((s) => (sourceName.value = s.name)).catch(() => {})
  await load()
})

watch(
  () => props.chapterUrl,
  () => {
    window.scrollTo(0, 0)
    barsVisible.value = true
    load()
  },
)

function imgSrc(page) {
  const base = api.imageUrl(props.sourceId, page)
  const n = retries.value[page.index] || 0
  return n ? `${base}&retry=${n}` : base
}

function onImgError(page) {
  failed.value = { ...failed.value, [page.index]: true }
}

function retry(page) {
  failed.value = { ...failed.value, [page.index]: false }
  retries.value = { ...retries.value, [page.index]: (retries.value[page.index] || 0) + 1 }
}

function go(ch) {
  if (ch) router.replace({ query: { manga: props.mangaUrl, chapter: ch.url } })
}

function toggleBars(e) {
  if (e.target.closest('button, a')) return
  barsVisible.value = !barsVisible.value
}

// Hide the bars while scrolling down, show them again when scrolling up
let lastY = 0
function onScroll() {
  const y = window.scrollY
  if (Math.abs(y - lastY) < 24) return
  barsVisible.value = y < lastY || y < 80
  lastY = y
}

function onKey(e) {
  if (e.key === 'ArrowRight' || e.key === ']') go(nextChapter.value)
  if (e.key === 'ArrowLeft' || e.key === '[') go(prevChapter.value)
}

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
  window.addEventListener('keydown', onKey)
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
  window.removeEventListener('keydown', onKey)
})
</script>

<template>
  <div class="reader" @click="toggleBars">
    <header class="bar top" :class="{ hidden: !barsVisible }">
      <RouterLink
        :to="{ name: 'manga', params: { sourceId }, query: { url: mangaUrl } }"
        class="bar-back"
        aria-label="返回漫画详情"
      >
        ‹
      </RouterLink>
      <div class="bar-title">
        <span class="bar-manga">{{ manga.title }}</span>
        <span class="bar-chapter">{{ chapter?.name }}</span>
      </div>
      <span v-if="pages.length" class="bar-count">{{ pages.length }} 页</span>
    </header>

    <div v-if="loading" class="status">
      <div class="halftone loading tone-block"></div>
      <p>正在读取这一话…</p>
    </div>

    <div v-else-if="error" class="status">
      <p>{{ error }}</p>
      <button class="btn" @click.stop="load">重试</button>
    </div>

    <ol v-else class="strip">
      <li v-for="p in pages" :key="p.index" class="page">
        <div v-if="failed[p.index]" class="page-failed">
          <p>第 {{ p.index + 1 }} 页没加载出来</p>
          <button class="btn" @click.stop="retry(p)">重新加载</button>
        </div>
        <img
          v-else
          :src="imgSrc(p)"
          :alt="`第 ${p.index + 1} 页`"
          :loading="p.index < 3 ? 'eager' : 'lazy'"
          decoding="async"
          :class="{ loaded: loaded[p.index] }"
          @load="loaded = { ...loaded, [p.index]: true }"
          @error="onImgError(p)"
        />
      </li>
    </ol>

    <nav v-if="!loading" class="chapter-nav" aria-label="章节导航">
      <button class="btn" :disabled="!prevChapter" @click.stop="go(prevChapter)">上一话</button>
      <RouterLink :to="{ name: 'manga', params: { sourceId }, query: { url: mangaUrl } }" class="btn">
        章节列表
      </RouterLink>
      <button class="btn primary" :disabled="!nextChapter" @click.stop="go(nextChapter)">
        {{ nextChapter ? '下一话' : '已是最新一话' }}
      </button>
    </nav>
  </div>
</template>

<style scoped>
.reader {
  --paper: #000;
  --ink: #f2f2f2;
  --tone: #262626;
  --pencil: #9a9a9a;
  --cobalt: #8ea2ff;
  background: #000;
  color: #f2f2f2;
  min-height: 100vh;
}

.bar {
  position: fixed;
  left: 0;
  right: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 14px;
  background: rgba(0, 0, 0, 0.88);
  border-bottom: 1px solid #333;
  transition: transform 0.2s ease;
}

.bar.top {
  top: 0;
}

.bar.top.hidden {
  transform: translateY(-100%);
}

@media (prefers-reduced-motion: reduce) {
  .bar {
    transition: none;
  }
}

.bar-back {
  font-size: 28px;
  line-height: 1;
  color: #f2f2f2;
  padding: 0 8px 4px;
}

.bar-back:hover {
  text-decoration: none;
}

.bar-title {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.bar-manga {
  font-size: var(--step--1);
  color: #9a9a9a;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.bar-chapter {
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.bar-count {
  font-size: var(--step--1);
  color: #9a9a9a;
  flex: none;
}

.status {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 24px;
  text-align: center;
  color: #9a9a9a;
}

.tone-block {
  width: 120px;
  height: 160px;
}

.strip {
  list-style: none;
  margin: 0 auto;
  padding: 0;
  max-width: 900px;
}

.page img {
  display: block;
  width: 100%;
  height: auto;
  /* unloaded pages reserve space so lazy loading doesn't fetch everything at once */
  min-height: 70vh;
  background: #111;
}

.page img.loaded {
  min-height: 0;
  background: none;
}

.page-failed {
  min-height: 300px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  border: 1px dashed #444;
  color: #9a9a9a;
}

.chapter-nav {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 10px;
  padding: 32px 16px 64px;
}

.chapter-nav .btn {
  min-width: 110px;
  justify-content: center;
}
</style>
