import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: '.',
  timeout: 90_000,
  retries: 0,
  reporter: 'list',
  outputDir: '/tmp/test-results',
  use: {
    baseURL: process.env.BASE_URL ?? 'http://localhost:3000',
    locale: 'pt-BR',
    timezoneId: 'America/Sao_Paulo',
    viewport: { width: 1280, height: 900 },
    screenshot: 'only-on-failure',
  },
});
