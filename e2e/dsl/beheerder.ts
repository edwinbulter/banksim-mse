import { expect } from '@playwright/test';
import type {
  BankDriver,
  RekeninghouderInBeheer,
  Spaarmutatie,
  TransactiesOpDatum,
  Uitkomst,
  Zoekopdracht,
} from '../drivers/bank-driver';
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
  private gezochtOp?: string;
  private uitkomst?: Uitkomst;

  constructor(
    private readonly maakDriver: () => Promise<BankDriver>,
    private readonly maakSchermDriver: () => Promise<BankDriver>,
    private readonly seed: BankSeed,
  ) {}

  /** S10.2: op het beheerscherm, in het protocol van het project; via REST is dat de API. */
  async zetSimulatiedatum(datum: string): Promise<void> {
    const driver = await this.opHetScherm();
    expect(await driver.zetSimulatiedatumOpScherm(datum), 'simulatiedatum zetten als beheerder').toBe('gelukt');
  }

  /** Zoals zetSimulatiedatum, maar de bank mag weigeren; de weigering volgt uit verwachtGeweigerd. */
  async probeertSimulatiedatumTeZetten(datum: string): Promise<void> {
    this.uitkomst = await (await this.opHetScherm()).zetSimulatiedatumOpScherm(datum);
  }

  /** S10.2 (FO): de datumkiezer is begrensd; de melding noemt wat beschikbaar is ("1 oktober 2021 t/m 31 december 2026"). */
  async verwachtGeweigerd(beschikbaar: string): Promise<void> {
    expect(this.uitkomst, 'simulatiedatum zetten als beheerder').toBe('geweigerd');
    expect(await (await this.opHetScherm()).simulatiedatumMelding(), 'melding op het beheerscherm').toBe(
      `Kies een datum van ${beschikbaar}.`,
    );
  }

  /** S10.2: "Terug naar vandaag" op het beheerscherm; via REST is dat de DELETE. */
  async zetBankTerugNaarVandaag(): Promise<void> {
    const driver = await this.opHetScherm();
    expect(await driver.wisSimulatiedatumOpScherm(), 'terug naar vandaag als beheerder').toBe('gelukt');
  }

  /** De datum waarop de bank staat ("vandaag" voor de bank, ADR 0013); "vandaag" is de echte datum in Europe/Amsterdam. */
  async verwachtSimulatiedatum(datum: string): Promise<void> {
    const verwacht = datum === 'vandaag' ? vandaagInAmsterdam() : datum;
    expect(await (await this.opHetScherm()).simulatiedatum(), 'simulatiedatum op het beheerscherm').toBe(verwacht);
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
    this.gezochtOp = klant.iban();
  }

  /**
   * Precies deze klant gevonden, met zijn rekeningen en de saldi op de simulatiedatum. Zonder spaarsaldo heeft de klant
   * geen spaarrekening. Is er nog niet op deze klant gezocht, dan zoekt de beheerder eerst.
   */
  async verwachtRekeninghouder(klant: Klant, { saldo, spaarsaldo }: { saldo: string; spaarsaldo?: string }): Promise<void> {
    if (this.gezochtOp !== klant.iban()) await this.zoektRekeninghouder(klant);
    const spaarrekening = spaarsaldo === undefined ? null : klant.spaarrekeningnummer();
    expect(this.gevonden, `rekeninghouders gevonden op ${klant.iban()}`).toEqual([
      { naam: klant.naam(), betaalrekening: klant.iban(), saldo, spaarrekening, spaarsaldo: spaarsaldo ?? null },
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
