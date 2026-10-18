import { test as basis } from '@playwright/test';
import { Bank } from './bank';
import { RestDriver } from '../drivers/rest/rest-driver';
import { UiDriver } from '../drivers/ui/ui-driver';

/** Per Playwright-project in te stellen: via de API (rest) of via de browser (ui). */
export interface Opties {
  driver: 'rest' | 'ui';
}

export const test = basis.extend<Opties & { bank: Bank }>({
  driver: ['rest', { option: true }],
  bank: async ({ driver, page, request }, use) => {
    const bankDriver = driver === 'ui' ? new UiDriver(page) : new RestDriver(request);
    await use(new Bank(bankDriver, (titel, stap) => basis.step(titel, stap)));
  },
});

export { expect } from '@playwright/test';
