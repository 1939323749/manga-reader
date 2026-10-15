<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const fullscreen = computed(() => route.meta.fullscreen === true)
</script>

<template>
  <RouterView v-if="fullscreen" />
  <div v-else class="shell">
    <nav class="rail" aria-label="主导航">
      <RouterLink to="/" class="brand">漫画</RouterLink>
      <RouterLink to="/" class="nav-link" exact-active-class="active">来源</RouterLink>
      <RouterLink to="/recent" class="nav-link" active-class="active">最近</RouterLink>
      <RouterLink to="/extensions" class="nav-link" active-class="active">扩展</RouterLink>
    </nav>
    <main class="content">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.shell {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr);
  min-height: 100vh;
}

.rail {
  position: sticky;
  top: 0;
  height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  border-right: var(--panel-border) solid var(--ink);
  padding: 18px 0;
}

.brand {
  font-family: var(--font-title);
  font-size: var(--step-2);
  color: var(--ink);
  text-align: center;
  writing-mode: vertical-rl;
  letter-spacing: 0.2em;
  margin: 0 auto 28px;
}

.brand:hover {
  text-decoration: none;
}

.nav-link {
  color: var(--ink);
  padding: 10px 0;
  text-align: center;
  font-size: var(--step-0);
  border-left: 4px solid transparent;
}

.nav-link:hover {
  text-decoration: none;
  background: var(--tone);
}

.nav-link.active {
  border-left-color: var(--cobalt);
  color: var(--cobalt);
  font-weight: 600;
}

.content {
  padding: 28px clamp(16px, 4vw, 48px) 64px;
  min-width: 0;
}

@media (max-width: 720px) {
  .shell {
    grid-template-columns: 1fr;
  }

  .rail {
    position: sticky;
    z-index: 5;
    height: auto;
    flex-direction: row;
    align-items: center;
    border-right: 0;
    border-bottom: var(--panel-border) solid var(--ink);
    background: var(--paper);
    padding: 0 16px;
    gap: 4px;
  }

  .brand {
    writing-mode: horizontal-tb;
    margin: 0 12px 0 0;
    font-size: var(--step-1);
  }

  .nav-link {
    padding: 12px 10px;
    border-left: 0;
    border-bottom: 4px solid transparent;
  }

  .nav-link.active {
    border-bottom-color: var(--cobalt);
  }

  .content {
    padding: 20px 16px 48px;
  }
}
</style>
