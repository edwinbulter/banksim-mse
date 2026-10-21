import type { BankDriver } from '../drivers/bank-driver';
import type { BankSeed } from '../drivers/shell/bank-seed';
import { Bezoeker } from './bezoeker';
import { Klant } from './klant';
import { Stappen, type Stap } from './stappen';

/** Ingang van de DSL voor de applicatie. */
export class Bank extends Stappen {
  /** Binnen één scenario is een alias steeds dezelfde klant, ook tussen beforeEach en de test. */
  private readonly klanten = new Map<string, Klant>();

  constructor(
    private readonly driver: BankDriver,
    private readonly seed: BankSeed,
    stap: Stap,
  ) {
    super(stap);
  }

  bezoeker(): Bezoeker {
    return new Bezoeker(this.driver);
  }

  klant(alias: string): Klant {
    let klant = this.klanten.get(alias);
    if (!klant) {
      klant = new Klant(alias, this.driver, this.seed);
      this.klanten.set(alias, klant);
    }
    return klant;
  }
}
