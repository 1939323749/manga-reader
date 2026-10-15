<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { api } from '../api.js'
import { lastReadChapter, lookupManga, rememberChapters, rememberManga } from '../store.js'

const props = defineProps({
  sourceId: { type: String, required: true },
  url: { type: String, required: true },
})

const manga = ref(lookupManga(props.sourceId, props.url))
const chapters = ref([])
const loading = ref(true)
const error = ref('')
const newestFirst = ref(true)
const expanded = ref(false)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await api.manga(props.sourceId, lookupManga(props.sourceId, props.url))
    manga.value = res.manga
    chapters.value = res.chapters
    rememberManga(props.sourceId, res.manga)
    rememberChapters(props.sourceId, props.url, res.chapters)
    if (res.manga.title) document.title = `${res.manga.title} | 漫画阅读`
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => props.url, () => {
  manga.value = lookupManga(props.sourceId, props.url)
  load()
})

const statusText = computed(
  () =>
    ({
      1: '连载中',
      2: '已完结',
      3: '已授权',
      4: '出版完结',
      5: '已取消',
      6: '休刊中',
    })[manga.value.status] || null,
)

const genres = computed(() =>
  (manga.value.genre || '')
    .split(',')
    .map((g) => g.trim())
    .filter(Boolean),
)

const credits = computed(() => {
  const { author, artist } = manga.value
  if (author && artist && author !== artist) return `作者 ${author}，作画 ${artist}`
  if (author || artist) return `作者 ${author || artist}`
  return null
})

const ordered = computed(() => (newestFirst.value ? chapters.value : [...chapters.value].reverse()))

const lastRead = computed(() => lastReadChapter(props.sourceId, props.url))

// Sources list newest first; the first chapter to read is the last in the list
const startChapter = computed(() => {
  if (lastRead.value && chapters.value.some((c) => c.url === lastRead.value.url)) return lastRead.value
  return chapters.value[chapters.value.length - 1] || null
})

function readLink(chapter) {
  return { name: 'reader', params: { sourceId: props.sourceId }, query: { manga: props.url, chapter: chapter.url } }
}

function formatDate(ms) {
  if (!ms) return ''
  const d = new Date(ms)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
</script>

<template>
  <RouterLink :to="{ name: 'browse', params: { sourceId } }" class="back">返回列表</RouterLink>

  <article class="manga">
    <div class="cover-panel halftone" :class="{ loading: loading && !manga.thumbnailUrl }">
      <img v-if="manga.thumbnailUrl" :src="api.coverUrl(sourceId, manga.thumbnailUrl)" alt="" />
    </div>

    <div class="meta">
      <h1>{{ manga.title || '…' }}</h1>
      <p v-if="credits" class="credits">{{ credits }}</p>
      <p v-if="statusText" class="status">{{ statusText }}</p>
      <ul v-if="genres.length" class="genres">
        <li v-for="g in genres" :key="g">{{ g }}</li>
      </ul>

      <div v-if="startChapter" class="cta">
        <RouterLink :to="readLink(startChapter)" class="btn primary">
          {{ lastRead && startChapter.url === lastRead.url ? `继续阅读 ${startChapter.name}` : '开始阅读' }}
        </RouterLink>
      </div>

      <div v-if="manga.description" class="desc" :class="{ clamped: !expanded }">
        <p>{{ manga.description }}</p>
      </div>
      <button v-if="manga.description && manga.description.length > 180" class="more" @click="expanded = !expanded">
        {{ expanded ? '收起简介' : '展开简介' }}
      </button>
    </div>
  </article>

  <div v-if="error" class="error-box">
    无法加载漫画信息：{{ error }}
    <button class="btn" @click="load">重试</button>
  </div>

  <section v-else class="chapters">
    <header class="chapters-head">
      <h2>章节 <span v-if="chapters.length" class="count muted">{{ chapters.length }}</span></h2>
      <button v-if="chapters.length > 1" class="btn" @click="newestFirst = !newestFirst">
        {{ newestFirst ? '从第一话排' : '从最新排' }}
      </button>
    </header>

    <div v-if="loading" class="halftone loading placeholder" aria-label="加载中"></div>
    <p v-else-if="!chapters.length" class="empty">这部漫画在该来源下没有章节。</p>
    <ol v-else class="chapter-list">
      <li v-for="c in ordered" :key="c.url">
        <RouterLink :to="readLink(c)" class="chapter" :class="{ current: lastRead?.url === c.url }">
          <span class="ch-name">{{ c.name }}</span>
          <span class="ch-sub muted">
            <template v-if="c.scanlator">{{ c.scanlator }}</template>
            <time v-if="c.dateUpload">{{ formatDate(c.dateUpload) }}</time>
          </span>
        </RouterLink>
      </li>
    </ol>
  </section>
</template>

<style scoped>
.back {
  font-size: var(--step--1);
}

.manga {
  display: grid;
  grid-template-columns: minmax(160px, 240px) minmax(0, 1fr);
  gap: 28px;
  margin: 14px 0 32px;
  align-items: start;
}

.cover-panel {
  aspect-ratio: 3 / 4;
  border: var(--panel-border) solid var(--ink);
  overflow: hidden;
}

.cover-panel img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.meta h1 {
  max-width: 24ch;
  overflow-wrap: anywhere;
}

.credits,
.status {
  margin: 8px 0 0;
}

.status {
  color: var(--pencil);
}

.genres {
  list-style: none;
  padding: 0;
  margin: 14px 0 0;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.genres li {
  border: 1px solid var(--ink);
  padding: 0 8px;
  font-size: var(--step--1);
}

.cta {
  margin: 20px 0 0;
}

.cta .btn {
  font-size: var(--step-0);
  padding: 9px 20px;
}

.desc {
  margin-top: 20px;
  max-width: 68ch;
  white-space: pre-line;
}

.desc p {
  margin: 0;
}

.desc.clamped {
  display: -webkit-box;
  -webkit-line-clamp: 5;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.more {
  border: 0;
  background: none;
  color: var(--cobalt);
  padding: 4px 0;
  font-size: var(--step--1);
}

.chapters-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  border-bottom: var(--panel-border) solid var(--ink);
  padding-bottom: 6px;
  max-width: 820px;
}

.chapters-head h2 {
  font-size: var(--step-1);
}

.count {
  font-family: var(--font-body);
  font-weight: 400;
  font-size: var(--step--1);
  margin-left: 6px;
}

.placeholder {
  height: 240px;
  margin-top: 12px;
  border: var(--panel-border) solid var(--ink);
  max-width: 820px;
}

.chapter-list {
  list-style: none;
  margin: 0;
  padding: 0;
  max-width: 820px;
}

.chapter {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 4px;
  border-bottom: 1px solid var(--tone);
  color: var(--ink);
}

.chapter:hover {
  text-decoration: none;
  background: var(--tone);
}

.chapter.current {
  border-left: 4px solid var(--cobalt);
  padding-left: 8px;
}

.chapter:visited .ch-name {
  color: var(--pencil);
}

.ch-sub {
  display: flex;
  gap: 12px;
  font-size: var(--step--1);
  flex: none;
}

@media (max-width: 600px) {
  .manga {
    grid-template-columns: minmax(0, 1fr);
    gap: 16px;
  }

  .cover-panel {
    width: 150px;
  }

  .meta h1 {
    font-size: var(--step-2);
  }

  .chapter {
    flex-direction: column;
    gap: 2px;
  }
}
</style>
