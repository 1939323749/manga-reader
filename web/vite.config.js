import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// The built UI is bundled into the server jar (served from the classpath at /web).
export default defineConfig({
  plugins: [vue()],
  build: {
    outDir: '../server/src/main/resources/web',
    emptyOutDir: true,
  },
  server: {
    proxy: {
      '/api': process.env.MANGA_READER_API ?? 'http://127.0.0.1:4567',
    },
  },
})
