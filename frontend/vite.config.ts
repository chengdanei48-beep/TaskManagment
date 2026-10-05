import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // 5173 が使われていても別のポート(5174 など)に逃げず、エラーで終了する(CLAUDE.md「動作確認でのサーバー起動」)
    strictPort: true,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
