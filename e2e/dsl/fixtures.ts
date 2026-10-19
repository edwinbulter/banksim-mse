import { test as basis } from '@playwright/test';
import { Bank } from './bank';
import { Omgeving } from './omgeving';
import { RestDriver } from '../drivers/rest/rest-driver';
import { ShellDriver } from '../drivers/shell/shell-driver';
import { UiDriver } from '../drivers/ui/ui-driver';

/** Per Playwright-project in te stellen: via de API (rest) of via de browser (ui). */
export interface Opties {
  driver: 'rest' | 'ui';
}

export const test = basis.extend<Opties & { bank: Bank; omgeving: Omgeving }>({
  driver: ['rest', { option: true }],
  bank: async ({ driver, page, request }, use) => {
    const bankDriver = driver === 'ui' ? new UiDriver(page) : new RestDriver(request);
    await use(new Bank(bankDriver, (titel, stap) => basis.step(titel, stap)));
  },
  // Installatiescenario's (project infra). BANKSIM_CONTEXT is in CI het tijdelijke cluster; BANKSIM_IMAGE_TAG de
  // images uit de commit stage. Zonder tag hergebruikt de omgeving de bestaande installatie (install.sh --skip-build).
  omgeving: async ({ request }, use) => {
    const shell = new ShellDriver(process.env.BANKSIM_CONTEXT ?? 'kind-single-node');
    const imageTag = process.env.BANKSIM_IMAGE_TAG || undefined;
    await use(new Omgeving(shell, new RestDriver(request), imageTag, (titel, stap) => basis.step(titel, stap)));
  },
});

export { expect } from '@playwright/test';
