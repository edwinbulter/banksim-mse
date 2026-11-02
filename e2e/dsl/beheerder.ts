import { expect } from '@playwright/test';
import type { BankDriver, RekeninghouderInBeheer } from '../drivers/bank-driver';
import type { BankSeed, GeseedeBeheerder } from '../drivers/shell/bank-seed';
import type { Klant } from './klant';

/**
 * De beheerder van de bank, met de realm-rol beheerder. Heeft een eigen sessie, los van de browser en de klanten:
 * anders zou een klant via de single sign-on van Keycloak als beheerder ingelogd raken. Tot het scherm van S10.2 werkt
 * de beheerder de simulatiedatum via de API; het beheerscherm (S10.1) gebruikt hij in het protocol van het project.
 */
export class Beheerder {
  private gegevens?: GeseedeBeheerder;
  private driver?: BankDriver;
  private schermDriver?: BankDriver;
  private gevonden: RekeninghouderInBeheer[] = [];

  constructor(
    private readonly maakDriver: () => Promise<BankDriver>,
    private readonly maakSchermDriver: () => Promise<BankDriver>,
    private readonly seed: BankSeed,
  ) {}

  async zetSimulatiedatum(datum: string): Promise<void> {
    const driver = await this.ingelogd();
    expect(await driver.zetSimulatiedatum(datum), 'simulatiedatum zetten als beheerder').toBe('gelukt');
  }

  /** S10.1: zoeken op het rekeningnummer van de klant; dat is uniek, de naam in de testdata niet. */
  async zoektRekeninghouder(klant: Klant): Promise<void> {
    this.gevonden = await (await this.opHetScherm()).zoekRekeninghouders(klant.iban());
  }

  /** Precies deze klant gevonden, met zijn rekeningen en de saldi op de simulatiedatum. */
  async verwachtRekeninghouder(klant: Klant, { saldo, spaarsaldo }: { saldo: string; spaarsaldo: string }): Promise<void> {
    expect(this.gevonden, `rekeninghouders gevonden op ${klant.iban()}`).toEqual([
      { naam: klant.naam(), betaalrekening: klant.iban(), saldo, spaarrekening: klant.spaarrekeningnummer(), spaarsaldo },
    ]);
  }

  /** De bank draait weer op vandaag; de fixture doet dit na elk scenario met @simulatiedatum. */
  async wistSimulatiedatum(): Promise<void> {
    const driver = await this.ingelogd();
    expect(await driver.wisSimulatiedatum(), 'simulatiedatum wissen als beheerder').toBe('gelukt');
  }

  private async ingelogd(): Promise<BankDriver> {
    if (!this.driver) {
      this.driver = await this.maakDriver();
      const { gebruikersnaam, wachtwoord } = await this.login();
      await this.driver.logIn(gebruikersnaam, wachtwoord);
    }
    return this.driver;
  }

  private async opHetScherm(): Promise<BankDriver> {
    if (!this.schermDriver) {
      this.schermDriver = await this.maakSchermDriver();
      const { gebruikersnaam, wachtwoord } = await this.login();
      await this.schermDriver.logIn(gebruikersnaam, wachtwoord);
    }
    return this.schermDriver;
  }

  private async login(): Promise<GeseedeBeheerder> {
    this.gegevens ??= await this.seed.beheerder('Beheerder');
    return this.gegevens;
  }
}
