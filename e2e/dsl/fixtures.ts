import { test as basis, type APIRequestContext } from '@playwright/test';
import { Bank } from './bank';
import { Omgeving } from './omgeving';
import { RestDriver } from '../drivers/rest/rest-driver';
import { BankSeed } from '../drivers/shell/bank-seed';
import { SeedSessie } from '../drivers/shell/seed-sessie';
import { ShellDriver } from '../drivers/shell/shell-driver';
import { UiDriver } from '../drivers/ui/ui-driver';

/** Per Playwright-project in te stellen: via de API (rest) of via de browser (ui). */
export interface Opties {
  driver: 'rest' | 'ui';
}

export const test = basis.extend<Opties & { bank: Bank; omgeving: Omgeving }, { seedSessie: SeedSessie | undefined }>({
  driver: ['rest', { option: true }],
  // ADR 0020: één seed-pod per worker, pas gestart bij de eerste opdracht. Niet in het project infra: daar wisselt een
  // upgrade de versie midden in de run, en een sessie zou de oude blijven draaien.
  seedSessie: [
    async ({}, use, workerInfo) => {
      if (workerInfo.project.name === 'infra') return use(undefined);
      const sessie = new SeedSessie(new ShellDriver(context()), 'banksim-mse');
      await use(sessie);
      await sessie.sluit();
    },
    { scope: 'worker' },
  ],
  bank: async ({ driver, page, request, playwright, baseURL, seedSessie }, use, testInfo) => {
    const bankDriver = driver === 'ui' ? new UiDriver(page) : new RestDriver(request);
    const seed = new BankSeed(new ShellDriver(context()), seedSessie);
    const eigenSessies: APIRequestContext[] = [];
    const bank = new Bank(bankDriver, seed, (titel, stap) => basis.step(titel, stap), async () => {
      const sessie = await playwright.request.newContext({ baseURL, ignoreHTTPSErrors: true });
      eigenSessies.push(sessie);
      return new RestDriver(sessie);
    });
    await use(bank);
    // De simulatiedatum geldt voor de hele bank: wissen, ook als het scenario faalde (ADR 0014).
    if (testInfo.tags.includes('@simulatiedatum')) {
      await basis.step('Na afloop wist de beheerder de simulatiedatum', () => bank.beheerder().wistSimulatiedatum());
    }
    await Promise.all(eigenSessies.map((sessie) => sessie.dispose()));
  },
  // Installatiescenario's (project infra). BANKSIM_CONTEXT is in CI het tijdelijke cluster; BANKSIM_IMAGE_TAG de
  // images uit de commit stage. Zonder tag hergebruikt de omgeving de bestaande installatie (install.sh --skip-build).
  omgeving: async ({ request }, use) => {
    const shell = new ShellDriver(context());
    const imageTag = process.env.BANKSIM_IMAGE_TAG || undefined;
    await use(new Omgeving(shell, new RestDriver(request), imageTag, (titel, stap) => basis.step(titel, stap)));
  },
});

/** De kubectl-context van de installatie; in CI het tijdelijke cluster. */
function context(): string {
  return process.env.BANKSIM_CONTEXT ?? 'kind-single-node';
}

export { expect } from '@playwright/test';
