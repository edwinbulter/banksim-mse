import { expect } from '@playwright/test';
import type { BankDriver } from '../drivers/bank-driver';
import type { BankSeed, GeseedeBeheerder } from '../drivers/shell/bank-seed';

/**
 * De beheerder van de bank, met de realm-rol beheerder. Heeft een eigen sessie, los van de browser en de klanten:
 * anders zou een klant via de single sign-on van Keycloak als beheerder ingelogd raken. Tot het scherm van S10.2 werkt
 * de beheerder via de API.
 */
export class Beheerder {
  private gegevens?: GeseedeBeheerder;
  private driver?: BankDriver;

  constructor(
    private readonly maakDriver: () => Promise<BankDriver>,
    private readonly seed: BankSeed,
  ) {}

  async zetSimulatiedatum(datum: string): Promise<void> {
    const driver = await this.ingelogd();
    expect(await driver.zetSimulatiedatum(datum), 'simulatiedatum zetten als beheerder').toBe('gelukt');
  }

  /** De bank draait weer op vandaag; de fixture doet dit na elk scenario met @simulatiedatum. */
  async wistSimulatiedatum(): Promise<void> {
    const driver = await this.ingelogd();
    expect(await driver.wisSimulatiedatum(), 'simulatiedatum wissen als beheerder').toBe('gelukt');
  }

  private async ingelogd(): Promise<BankDriver> {
    if (!this.driver) {
      this.gegevens = await this.seed.beheerder('Beheerder');
      this.driver = await this.maakDriver();
      await this.driver.logIn(this.gegevens.gebruikersnaam, this.gegevens.wachtwoord);
    }
    return this.driver;
  }
}
