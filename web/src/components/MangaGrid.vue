<script setup>
// Covers laid out like a manga page: black-bordered panels with white gutters.
import { api } from '../api.js'
import { rememberManga } from '../store.js'

const props = defineProps({
  sourceId: { type: String, required: true },
  mangas: { type: Array, required: true },
})

function remember(m) {
  rememberManga(props.sourceId, m)
}
</script>

<template>
  <ul class="panels">
    <li v-for="m in mangas" :key="m.url" class="panel">
      <RouterLink
        :to="{ name: 'manga', params: { sourceId }, query: { url: m.url } }"
        class="panel-link"
        @click="remember(m)"
      >
        <div class="cover halftone">
          <img
            v-if="m.thumbnailUrl"
            :src="api.coverUrl(sourceId, m.thumbnailUrl)"
            :alt="''"
            loading="lazy"
            decoding="async"
            @error="(e) => (e.target.style.visibility = 'hidden')"
          />
        </div>
        <span class="title">{{ m.title || '未命名' }}</span>
      </RouterLink>
    </li>
  </ul>
</template>

<style scoped>
.panels {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: var(--gutter);
}

/* Every 7th panel spans two columns on wide screens, like an establishing shot */
@media (min-width: 900px) {
  .panel:nth-child(7n + 1) {
    grid-column: span 2;
  }

  .panel:nth-child(7n + 1) .cover {
    aspect-ratio: 4 / 3;
  }
}

.panel-link {
  display: flex;
  flex-direction: column;
  height: 100%;
  border: var(--panel-border) solid var(--ink);
  color: var(--ink);
  background: var(--paper);
}

.panel-link:hover {
  text-decoration: none;
}

.panel-link:hover .title {
  background: var(--ink);
  color: var(--paper);
}

.cover {
  aspect-ratio: 3 / 4;
  overflow: hidden;
  border-bottom: var(--panel-border) solid var(--ink);
}

.cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.title {
  padding: 6px 8px 8px;
  font-size: var(--step--1);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  flex: 1;
}

@media (max-width: 480px) {
  .panels {
    grid-template-columns: repeat(auto-fill, minmax(110px, 1fr));
    gap: 8px;
  }
}
</style>
