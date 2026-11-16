import { defineConfig, devices } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import type { Opties } from './dsl/fixtures';

/**
 * Acceptatietests tegen de installatie in namespace banksim-mse (doc/werkwijze-mse.md, hoofdstuk 5).
 * Hetzelfde scenario draait via de API (project rest) en via de browser (chromium). Firefox en webkit draaien alleen
 * de scenario's met @ui of @rook (ADR 0019).
 * @wip-scenario's tellen niet mee; @ui-scenario's draaien alleen in de browser.
 * @simulatiedatum-scenario's veranderen de datum van de hele bank (ADR 0014). Ze draaien daarom pas na alle andere,
 * in een keten van projecten (rest, dan chromium), met één worker.
 * Het project infra draait de installatiescenario's (hoofdstuk 9.2) met een shell-driver; @destructief alleen
 * met BANKSIM_DESTRUCTIEF=1, in het CI-cluster.
 */
const baseURL = process.env.BANKSIM_URL ?? 'https://bank-mse.localtest.me';
const app = { testIgnore: 'infra/**' };
const destructief = process.env.BANKSIM_DESTRUCTIEF === '1';
const gewoon = ['rest', 'chromium', 'firefox', 'webkit'];
// ADR 0053: de omgeving en de categorieën van fouten in het Allure-rapport. De omgeving is in alle CI-jobs gelijk, want
// Allure houdt na het samenvoegen er maar één over.
const allureOmgeving = {
  'Bank': baseURL,
  'Image-tag': process.env.BANKSIM_IMAGE_TAG ?? process.env.GITHUB_SHA?.slice(0, 7) ?? 'lokale installatie',
  'Playwright': (createRequire(import.meta.url)('@playwright/test/package.json') as { version: string }).version,
  'Node': process.version,
};
const allureCategorieen = JSON.parse(readFileSync(new URL('./allure/categories.json', import.meta.url), 'utf-8'));
const simulatiedatum = { ...app, grep: /@simulatiedatum/, fullyParallel: false, workers: 1 };

export default defineConfig<Opties>({
  testDir: './specs',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  // Backlog fase 3.7: geen herkansing, ook niet in CI. Een flaky scenario maakt de pipeline rood en valt zo op.
  retries: 0,
  timeout: 30_000,
  expect: { timeout: 10_000 },
  // ADR 0053: naast het HTML-rapport (met de trace viewer) de resultaten voor Allure; make allure maakt er een rapport van.
  // Zonder detail: alleen de stappen van de DSL, niet elke klik en elke fill. Een fill toont zijn waarde, ook een
  // wachtwoord, en het Allure-rapport is openbaar. Om dezelfde reden krijgt Allure de trace niet.
  reporter: [
    ['list'],
    ['html', { open: 'never' }],
    [
      './rapport/allure-zonder-trace.ts',
      { resultsDir: 'allure-results', detail: false, environmentInfo: allureOmgeving, categories: allureCategorieen },
    ],
  ],
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
    // ADR 0019: alleen het scherm zelf (@ui) en het hele pad met inloggen (@rook).
    { name: 'firefox', ...app, grep: /@ui|@rook/, grepInvert: /@wip|@simulatiedatum/, use: { ...devices['Desktop Firefox'], driver: 'ui' } },
    { name: 'webkit', ...app, grep: /@ui|@rook/, grepInvert: /@wip|@simulatiedatum/, use: { ...devices['Desktop Safari'], driver: 'ui' } },
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
      name: 'infra',
      testDir: './specs/infra',
      // Zodat --shard per scenario verdeelt en niet per bestand (ADR 0047). Met één worker draaien ze nog steeds
      // na elkaar; de serial-groep in installatie.spec.ts blijft bij elkaar, in de volgorde van het bestand.
      fullyParallel: true,
      // Geen herkansing: een falende installatie is een signaal, en een herkansing van een serial-groep
      // verwijdert en installeert opnieuw.
      retries: 0,
      // Eén installatie per cluster: de specs mogen elkaar niet tegelijk installeren of verwijderen. In CI heeft
      // elke shard een eigen cluster. De job telemetrie zet dit hoger: die scenario's installeren niets zelf (ADR 0054).
      workers: Number(process.env.BANKSIM_INFRA_WORKERS ?? 1),
      // Een installatie wacht op Helm en de rooktests.
      timeout: 10 * 60_000,
      grepInvert: destructief ? /@wip/ : /@wip|@destructief/,
    },
  ],
});
