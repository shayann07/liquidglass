import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir: '.', testMatch: 'browser.spec.mjs', timeout: 30000,
  use: { baseURL: 'http://127.0.0.1:8766', viewport: {width:1200,height:900}, headless: true },
  webServer: { command:'node serve.mjs', url:'http://127.0.0.1:8766', reuseExistingServer: !process.env.CI },
  reporter: [['list'],['html',{open:'never'}]],
});
