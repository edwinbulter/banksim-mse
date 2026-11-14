import { createRequire } from 'node:module';
import type { TestCase, TestResult } from '@playwright/test/reporter';
import type AllureReporterKlasse from 'allure-playwright';

// allure-playwright is CommonJS; vanuit een ES-module staat de klasse onder default.
const AllureReporter: typeof AllureReporterKlasse = createRequire(import.meta.url)('allure-playwright').default;

/**
 * allure-playwright, maar zonder de trace van Playwright (ADR 0053). Een trace bevat elke fill met zijn waarde, ook
 * het wachtwoord van de testklant, en het Allure-rapport is openbaar. De trace blijft in het HTML-rapport van
 * Playwright, het artifact van de job; de screenshot en de andere bijlagen gaan wel naar Allure.
 */
export default class AllureZonderTrace extends AllureReporter {
  override async onTestEnd(test: TestCase, result: TestResult): Promise<void> {
    await super.onTestEnd(test, { ...result, attachments: result.attachments.filter((bijlage) => bijlage.name !== 'trace') });
  }
}
