import type { BankDriver } from '../drivers/bank-driver';
import { Bezoeker } from './bezoeker';

type Stap = (titel: string, stap: () => Promise<unknown>) => Promise<unknown>;

/**
 * Ingang van de DSL. De stappen maken van een Playwright-test een leesbaar scenario in het rapport;
 * de code in een stap roept alleen DSL-methodes aan, nooit de drivers.
 */
export class Bank {
  constructor(
    private readonly driver: BankDriver,
    private readonly stap: Stap,
  ) {}

  bezoeker(): Bezoeker {
    return new Bezoeker(this.driver);
  }

  async gegeven(tekst: string, stap: () => Promise<unknown>): Promise<void> {
    await this.stap(`Gegeven ${tekst}`, stap);
  }

  async als(tekst: string, stap: () => Promise<unknown>): Promise<void> {
    await this.stap(`Als ${tekst}`, stap);
  }

  async dan(tekst: string, stap: () => Promise<unknown>): Promise<void> {
    await this.stap(`Dan ${tekst}`, stap);
  }

  async en(tekst: string, stap: () => Promise<unknown>): Promise<void> {
    await this.stap(`En ${tekst}`, stap);
  }

  async maar(tekst: string, stap: () => Promise<unknown>): Promise<void> {
    await this.stap(`Maar ${tekst}`, stap);
  }
}
