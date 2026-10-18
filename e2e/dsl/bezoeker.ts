import { expect } from '@playwright/test';
import type { BankDriver } from '../drivers/bank-driver';

/** Iemand die BankSim opent zonder in te loggen. */
export class Bezoeker {
  constructor(private readonly driver: BankDriver) {}

  async opentBankSim(): Promise<void> {
    await this.driver.openStartpagina();
  }

  async verwachtVersie(): Promise<void> {
    expect(await this.driver.versie()).toMatch(/^\S+$/);
  }
}
