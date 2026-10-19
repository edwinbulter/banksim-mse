import type { BankDriver } from '../drivers/bank-driver';
import { Bezoeker } from './bezoeker';
import { Stappen, type Stap } from './stappen';

/** Ingang van de DSL voor de applicatie. */
export class Bank extends Stappen {
  constructor(
    private readonly driver: BankDriver,
    stap: Stap,
  ) {
    super(stap);
  }

  bezoeker(): Bezoeker {
    return new Bezoeker(this.driver);
  }
}
