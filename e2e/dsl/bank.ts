import type { BankDriver } from '../drivers/bank-driver';
import type { BankSeed } from '../drivers/shell/bank-seed';
import { Beheerder } from './beheerder';
import { Bezoeker } from './bezoeker';
import { Klant } from './klant';
import { Stappen, type Stap } from './stappen';

/** Ingang van de DSL voor de applicatie. */
export class Bank extends Stappen {
  /** Binnen één scenario is een alias steeds dezelfde klant, ook tussen beforeEach en de test. */
  private readonly klanten = new Map<string, Klant>();
  private eenBeheerder?: Beheerder;
  private driverVanHetScenarioVrij = true;

  constructor(
    private readonly driver: BankDriver,
    private readonly seed: BankSeed,
    stap: Stap,
    /** Een driver met een eigen sessie, voor de beheerder. */
    private readonly maakBeheerderDriver: () => Promise<BankDriver>,
    /** Een driver met een eigen sessie in hetzelfde protocol (REST of browser), voor een tweede klant (S7.1). */
    private readonly maakKlantDriver: () => Promise<BankDriver>,
    /** Hetzelfde voor de beheerder op het beheerscherm (S10.1); de simulatiedatum zet hij tot S10.2 via de API. */
    private readonly maakBeheerschermDriver: () => Promise<BankDriver>,
    /** Een browser met een eigen sessie, ook in een REST-project: wat alleen een browser doet (cookies voor JavaScript). */
    private readonly maakBrowserDriver: () => Promise<BankDriver>,
  ) {
    super(stap);
  }

  bezoeker(): Bezoeker {
    return new Bezoeker(this.driver);
  }

  beheerder(): Beheerder {
    this.eenBeheerder ??= new Beheerder(this.maakBeheerderDriver, this.maakBeheerschermDriver, this.seed);
    return this.eenBeheerder;
  }

  /** De eerste klant krijgt de driver van het scenario; een volgende een eigen, anders zou hij in de sessie van de eerste zitten. */
  private async kiesKlantDriver(): Promise<BankDriver> {
    if (!this.driverVanHetScenarioVrij) return this.maakKlantDriver();
    this.driverVanHetScenarioVrij = false;
    return this.driver;
  }

  klant(alias: string): Klant {
    let klant = this.klanten.get(alias);
    if (!klant) {
      klant = new Klant(alias, () => this.kiesKlantDriver(), this.seed, this.maakBrowserDriver);
      this.klanten.set(alias, klant);
    }
    return klant;
  }
}
