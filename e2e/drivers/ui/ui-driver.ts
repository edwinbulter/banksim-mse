import type { Page } from '@playwright/test';
import type { BankDriver } from '../bank-driver';
import { StartPagina } from './start-pagina';

/** Spreekt BankSim aan via de browser, met page objects per scherm. */
export class UiDriver implements BankDriver {
  private readonly start: StartPagina;

  constructor(page: Page) {
    this.start = new StartPagina(page);
  }

  async openStartpagina(): Promise<void> {
    await this.start.open();
  }

  async versie(): Promise<string> {
    return this.start.versie();
  }
}
