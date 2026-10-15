<script setup>
import { ref } from 'vue'
import { api } from '../api.js'
import { clearHistory, readHistory } from '../store.js'

const history = ref(readHistory())

function clear() {
  clearHistory()
  history.value = []
}

function when(ms) {
  const diff = (Date.now() - ms) / 1000
  if (diff < 3600) return `${Math.max(1, Math.round(diff / 60))} 分钟前`
  if (diff < 86400) return `${Math.round(diff / 3600)} 小时前`
  if (diff < 86400 * 30) return `${Math.round(diff / 86400)} 天前`
  return new Date(ms).toLocaleDateString('zh-CN')
}
</script>

<template>
  <header class="head">
    <h1>最近阅读</h1>
    <button v-if="history.length" class="btn" @click="clear">清空记录</button>
  </header>

  <div v-if="!history.length" class="empty">
    <p>还没有阅读记录。阅读记录只保存在这个浏览器里。</p>
    <RouterLink to="/" class="btn primary">去找漫画</RouterLink>
  </div>

  <ul v-else class="list">
    <li v-for="h in history" :key="h.sourceId + h.manga.url">
      <RouterLink
        :to="{ name: 'reader', params: { sourceId: h.sourceId }, query: { manga: h.manga.url, chapter: h.chapter.url } }"
        class="row"
      >
        <span class="thumb halftone">
          <img v-if="h.manga.thumbnailUrl" :src="api.coverUrl(h.sourceId, h.manga.thumbnailUrl)" alt="" loading="lazy" />
        </span>
        <span class="info">
          <span class="title">{{ h.manga.title }}</span>
          <span class="chapter">读到 {{ h.chapter.name }}</span>
          <span class="muted small">{{ h.sourceName }}<template v-if="h.sourceName">，</template>{{ when(h.at) }}</span>
        </span>
      </RouterLink>
    </li>
  </ul>
</template>

<style scoped>
.head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}

.list {
  list-style: none;
  margin: 0;
  padding: 0;
  max-width: 820px;
}

.row {
  display: flex;
  gap: 14px;
  padding: 10px 0;
  border-bottom: 1px solid var(--tone);
  color: var(--ink);
}

.row:hover {
  text-decoration: none;
  background: var(--tone);
}

.thumb {
  width: 60px;
  aspect-ratio: 3 / 4;
  flex: none;
  border: 2px solid var(--ink);
  overflow: hidden;
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.info {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-width: 0;
}

.title {
  font-family: var(--font-title);
  font-weight: 700;
  font-size: var(--step-1);
  overflow-wrap: anywhere;
}

.small {
  font-size: var(--step--1);
}
</style>
