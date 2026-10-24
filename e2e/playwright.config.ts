import { defineConfig, devices } from '@playwright/test';
import type { Opties } from './dsl/fixtures';

/**
 * Acceptatietests tegen de installatie in namespace banksim-mse (doc/werkwijze-mse.md, hoofdstuk 5).
 * Hetzelfde scenario draait via de API (project rest) en via de browser (chromium, firefox, webkit).
 * @wip-scenario's tellen niet mee; @ui-scenario's draaien alleen in de browser.
 * @simulatiedatum-scenario's veranderen de datum van de hele bank (ADR 0014). Ze draaien daarom pas na alle andere,
 * in een keten van projecten (rest, dan chromium, firefox, webkit), met één worker.
 * Het project infra draait de installatiescenario's (hoofdstuk 9.2) met een shell-driver; @destructief alleen
 * met BANKSIM_DESTRUCTIEF=1, in het CI-cluster.
 */
const baseURL = process.env.BANKSIM_URL ?? 'https://bank-mse.localtest.me';
const app = { testIgnore: 'infra/**' };
const destructief = process.env.BANKSIM_DESTRUCTIEF === '1';
const gewoon = ['rest', 'chromium', 'firefox', 'webkit'];
const simulatiedatum = { ...app, grep: /@simulatiedatum/, fullyParallel: false, workers: 1 };

export default defineConfig<Opties>({
  testDir: './specs',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  // De standaard is de helft van de cores: 2 op de CI-runner. De scenario's wachten vooral op de seed-pods en het
  // cluster, niet op de CPU van de runner; lokaal gebruikt Playwright er al meer.
  workers: process.env.CI ? 4 : undefined,
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
    { name: 'rest', ...app, grepInvert: /@wip|@ui|@simulatiedatum/, use: { driver: 'rest' } },
    { name: 'chromium', ...app, grepInvert: /@wip|@simulatiedatum/, use: { ...devices['Desktop Chrome'], driver: 'ui' } },
    { name: 'firefox', ...app, grepInvert: /@wip|@simulatiedatum/, use: { ...devices['Desktop Firefox'], driver: 'ui' } },
    { name: 'webkit', ...app, grepInvert: /@wip|@simulatiedatum/, use: { ...devices['Desktop Safari'], driver: 'ui' } },
    {
      name: 'simulatiedatum-rest',
      ...simulatiedatum,
      grepInvert: /@wip|@ui/,
      dependencies: gewoon,
      use: { driver: 'rest' },
    },
    {
      name: 'simulatiedatum-chromium',
      ...simulatiedatum,
      grepInvert: /@wip/,
      dependencies: ['simulatiedatum-rest'],
      use: { ...devices['Desktop Chrome'], driver: 'ui' },
    },
    {
      name: 'simulatiedatum-firefox',
      ...simulatiedatum,
      grepInvert: /@wip/,
      dependencies: ['simulatiedatum-chromium'],
      use: { ...devices['Desktop Firefox'], driver: 'ui' },
    },
    {
      name: 'simulatiedatum-webkit',
      ...simulatiedatum,
      grepInvert: /@wip/,
      dependencies: ['simulatiedatum-firefox'],
      use: { ...devices['Desktop Safari'], driver: 'ui' },
    },
    {
      name: 'infra',
      testDir: './specs/infra',
      fullyParallel: false,
      // Geen herkansing: een falende installatie is een signaal, en een herkansing van een serial-groep
      // verwijdert en installeert opnieuw.
      retries: 0,
      // Eén installatie: de specs mogen elkaar niet tegelijk installeren of verwijderen.
      workers: 1,
      // Een installatie wacht op Helm en de rooktests.
      timeout: 10 * 60_000,
      grepInvert: destructief ? /@wip/ : /@wip|@destructief/,
    },
  ],
});
