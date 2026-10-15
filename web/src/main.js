import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import Sources from './views/Sources.vue'
import Extensions from './views/Extensions.vue'
import Recent from './views/Recent.vue'
import Browse from './views/Browse.vue'
import Manga from './views/Manga.vue'
import Reader from './views/Reader.vue'
import './style.css'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'sources', component: Sources, meta: { title: '来源' } },
    { path: '/extensions', name: 'extensions', component: Extensions, meta: { title: '扩展' } },
    { path: '/recent', name: 'recent', component: Recent, meta: { title: '最近阅读' } },
    { path: '/source/:sourceId', name: 'browse', component: Browse, props: true },
    { path: '/source/:sourceId/manga', name: 'manga', component: Manga, props: (r) => ({ sourceId: r.params.sourceId, url: r.query.url }) },
    {
      path: '/source/:sourceId/read',
      name: 'reader',
      component: Reader,
      props: (r) => ({ sourceId: r.params.sourceId, mangaUrl: r.query.manga, chapterUrl: r.query.chapter }),
      meta: { fullscreen: true },
    },
  ],
  scrollBehavior(to, from, saved) {
    return saved || { top: 0 }
  },
})

router.afterEach((to) => {
  if (to.meta.title) document.title = `${to.meta.title} | 漫画阅读`
})

createApp(App).use(router).mount('#app')
