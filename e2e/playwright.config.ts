import { defineConfig, devices } from '@playwright/test';
import type { Opties } from './dsl/fixtures';

/**
 * Acceptatietests tegen de installatie in namespace banksim-mse (doc/werkwijze-mse.md, hoofdstuk 5).
 * Hetzelfde scenario draait via de API (project rest) en via de browser (chromium, firefox, webkit).
 * @wip-scenario's tellen niet mee; @ui-scenario's draaien alleen in de browser.
 * Het project infra draait de installatiescenario's (hoofdstuk 9.2) met een shell-driver; @destructief alleen
 * met BANKSIM_DESTRUCTIEF=1, in het CI-cluster.
 */
const baseURL = process.env.BANKSIM_URL ?? 'https://bank-mse.localtest.me';
const app = { testIgnore: 'infra/**' };
const destructief = process.env.BANKSIM_DESTRUCTIEF === '1';

export default defineConfig<Opties>({
  testDir: './specs',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  timeout: 30_000,
  expect: { timeout: 10_000 },
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL,
    locale: 'nl-NL',
    timezoneId: 'Europe/Amsterdam',
    // Nog geen eigen CA: ingress-nginx toont zijn standaardcertificaat. Wordt strenger in de CFR-slice zero trust.
    ignoreHTTPSErrors: true,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'rest', ...app, grepInvert: /@wip|@ui/, use: { driver: 'rest' } },
    { name: 'chromium', ...app, grepInvert: /@wip/, use: { ...devices['Desktop Chrome'], driver: 'ui' } },
    { name: 'firefox', ...app, grepInvert: /@wip/, use: { ...devices['Desktop Firefox'], driver: 'ui' } },
    { name: 'webkit', ...app, grepInvert: /@wip/, use: { ...devices['Desktop Safari'], driver: 'ui' } },
    {
      name: 'infra',
      testDir: './specs/infra',
      fullyParallel: false,
      // Eén installatie: de specs mogen elkaar niet tegelijk installeren of verwijderen.
      workers: 1,
      // Een installatie wacht op Helm en de rooktests.
      timeout: 10 * 60_000,
      grepInvert: destructief ? /@wip/ : /@wip|@destructief/,
    },
  ],
});
