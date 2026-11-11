import { defineConfig, devices } from '@playwright/test';

/**
 * De schermafdrukken voor doc/banksim-mse-handleiding.md, met `make handleiding`. Geen test: het script kijkt alleen en wijzigt
 * niets (geen betaling, geen overschrijving, geen simulatiedatum), zodat de demo-data gelijk blijft aan de golden master.
 */
export default defineConfig({
  testDir: './handleiding',
  testMatch: '*.handleiding.ts',
  workers: 1,
  retries: 0,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: [['list']],
  use: {
    ...devices['Desktop Chrome'],
    baseURL: process.env.BANKSIM_URL ?? 'https://bank-mse.localtest.me',
    viewport: { width: 1280, height: 800 },
    locale: 'nl-NL',
    timezoneId: 'Europe/Amsterdam',
    // Net als de acceptatietests: ingress-nginx toont zijn standaardcertificaat.
    ignoreHTTPSErrors: true,
  },
});
