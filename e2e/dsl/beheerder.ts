import { expect } from '@playwright/test';
import type { BankDriver, RekeninghouderInBeheer, Spaarmutatie, TransactiesOpDatum, Zoekopdracht } from '../drivers/bank-driver';
import type { BankSeed, GeseedeBeheerder } from '../drivers/shell/bank-seed';
import { type Klant, vandaagInAmsterdam } from './klant';

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

  /** Inloggen in het protocol van het project, zoals de beheerder dat doet. */
  async logtIn(): Promise<void> {
    await this.opHetScherm();
  }

  /** S10.1, ADR 0037: na het inloggen staat de beheerder op het beheerscherm, niet op een overzicht. */
  async verwachtBeheerscherm(): Promise<void> {
    expect(await (await this.opHetScherm()).scherm(), 'scherm na het inloggen').toBe('beheer');
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

  /** S10.1: de betaalrekening van een klant, alleen-lezen. */
  async opentBetaalrekeningVan(klant: Klant): Promise<void> {
    await (await this.opHetScherm()).openRekeningInBeheer(klant.iban());
  }

  async verwachtTransactielijst(verwacht: TransactiesOpDatum[]): Promise<void> {
    expect(await (await this.opHetScherm()).transacties(), 'transacties in beheer').toEqual(verwacht);
  }

  /** Alleen-lezen: betalen vanaf de rekening van de klant lukt niet. */
  async verwachtAlleenLezen(): Promise<void> {
    expect(await (await this.opHetScherm()).betaalInBeheer(), 'betalen als beheerder').toBe('geweigerd');
  }

  /** Op de geopende rekening van een klant, zoals de klant zoekt (S5.1). */
  async zoekt(opdracht: Zoekopdracht): Promise<void> {
    await (await this.opHetScherm()).zoek(opdracht);
  }

  /** De tegenpartijen in de lijst, nieuw naar oud; leeg als er niets gevonden is. */
  async verwachtGevonden(tegenpartijen: string[]): Promise<void> {
    const gevonden = (await (await this.opHetScherm()).transacties()).flatMap((dag) => dag.transacties.map((t) => t.tegenpartij));
    expect(gevonden, 'gevonden transacties in beheer').toEqual(tegenpartijen);
  }

  /** S10.1: de spaarrekening van een klant, alleen-lezen. */
  async opentSpaarrekeningVan(klant: Klant): Promise<void> {
    await (await this.opHetScherm()).openSpaarrekeningInBeheer(klant.spaarrekeningnummer());
  }

  /** Zoals Klant.verwachtSpaarmutatie: deze mutatie staat erbij; `op` is een datum of "vandaag". */
  async verwachtSpaarmutatie({ op, type, bedrag }: { op: string } & Omit<Spaarmutatie, 'datum' | 'omschrijving'>): Promise<void> {
    const datum = op === 'vandaag' ? vandaagInAmsterdam() : op;
    expect(await (await this.opHetScherm()).spaarmutaties(), 'spaarmutaties in beheer').toContainEqual({
      datum,
      type,
      omschrijving: type,
      bedrag,
    });
  }

  /** Alleen-lezen: opnemen van de spaarrekening van de klant naar zijn betaalrekening lukt niet. */
  async verwachtSpaarrekeningAlleenLezen(klant: Klant): Promise<void> {
    const opdracht = { van: klant.spaarrekeningnummer(), naar: klant.iban(), bedrag: '0.01' };
    expect(await (await this.opHetScherm()).neemOpInBeheer(opdracht), 'opnemen als beheerder').toBe('geweigerd');
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
