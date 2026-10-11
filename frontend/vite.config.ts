/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.ts',
    // Fuso fixo: testes de data não podem depender da máquina ou do CI (que roda em UTC).
    env: { TZ: 'America/Sao_Paulo' },
    css: false,
  },
})
