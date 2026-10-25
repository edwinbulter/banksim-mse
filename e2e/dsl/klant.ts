import { expect } from '@playwright/test';
import type {
  BankDriver,
  Soort,
  TransactieDetails,
  TransactieType,
  TransactiesOpDatum,
  Uitkomst,
  Zoekformulier,
  Zoekopdracht,
} from '../drivers/bank-driver';

interface GeseedeTransactie {
  datum: string;
  tegenpartij: string;
  bedrag: string;
}
import type { BankSeed, GeseedeKlant } from '../drivers/shell/bank-seed';

/** Een betaling of ontvangst uit "Gegeven"; zonder type is het Online bankieren, zonder tegenIban is er geen IBAN. */
interface Betaling {
  op: string;
  bedrag: string;
  tegenIban?: string;
  type?: TransactieType;
}

/** Een boeking met teken ("-812.40" is een afschrijving), tijdstip in Europe/Amsterdam, type en tegenpartij (S5.1). */
export interface Boeking {
  op: string;
  bedrag: string;
  type: TransactieType;
  tegenpartij: string;
  tegenIban?: string;
}

/**
 * Een rekeninghouder, aangesproken met een alias ("Jan"). De seed maakt per alias een unieke klant aan (ADR 0003),
 * zodat scenario's elkaars data niet zien.
 */
export class Klant {
  private gegevens?: GeseedeKlant;
  /** Wat heeftTransacties boekte, in boekingsvolgorde. */
  private geseed: GeseedeTransactie[] = [];
  private ingelogd = false;
  private uitkomst?: Uitkomst;

  private eigenDriver?: BankDriver;

  constructor(
    private readonly alias: string,
    /** De sessie van deze klant: de eerste klant die hem nodig heeft, krijgt die van het scenario, de rest een eigen. */
    private readonly kiesDriver: () => Promise<BankDriver>,
    private readonly seed: BankSeed,
  ) {}

  private async sessie(): Promise<BankDriver> {
    this.eigenDriver ??= await this.kiesDriver();
    return this.eigenDriver;
  }

  /**
   * De alias is ook de naam van de rekeninghouder, zoals Van en Naar hem tonen. Zonder saldo is het 0,00: het scenario
   * zegt dan niets over het saldo, maar de seed heeft er een nodig. Met `contact` komt de betaalrekening met die naam in
   * de contactenlijst van de bank (S7.1).
   */
  async heeftBetaalrekening({ saldo = '0.00', contact = false }: { saldo?: string; contact?: boolean } = {}): Promise<void> {
    this.gegevens = await this.seed.klantMetBetaalrekening(this.alias, saldo);
    if (contact) await this.seed.contact(this.gegevens.iban, this.alias);
  }

  /** `op` is een datum ("2026-10-01") of een tijdstip in Europe/Amsterdam ("2026-10-01T06:02"). */
  async heeftBetaald({ op, bedrag, aan, tegenIban, type }: Betaling & { aan: string }): Promise<void> {
    await this.seed.transactie(this.vereisGegevens().iban, op, `-${bedrag}`, aan, { tegenIban, type });
  }

  async heeftOntvangen({ op, bedrag, van, tegenIban, type }: Betaling & { van: string }): Promise<void> {
    await this.seed.transactie(this.vereisGegevens().iban, op, bedrag, van, { tegenIban, type });
  }

  /** Alle boekingen in één keer, in deze volgorde. */
  async heeftBoekingen(boekingen: Boeking[]): Promise<void> {
    await this.seed.boekingen(this.vereisGegevens().iban, boekingen);
  }

  /** De naam van de rekeninghouder: de alias (zie heeftBetaalrekening). */
  naam(): string {
    return this.alias;
  }

  /** Het IBAN van de betaalrekening uit "Gegeven", voor de verwachting bij Van of Naar. */
  iban(): string {
    return this.vereisGegevens().iban;
  }

  /**
   * Een reeks afschrijvingen vanaf 1 januari 2026, `perDag` per dag: "Tegenpartij 1" (€ 0,01) tot en met
   * "Tegenpartij <aantal>". Elke tegenpartij en elk bedrag is uniek, zodat een dubbele of ontbrekende transactie opvalt.
   */
  async heeftTransacties({ aantal, perDag }: { aantal: number; perDag: number }): Promise<void> {
    const transacties = Array.from({ length: aantal }, (_, i) => ({
      datum: dagNa('2026-01-01', Math.floor(i / perDag)),
      tegenpartij: `Tegenpartij ${i + 1}`,
      bedrag: `-${((i + 1) / 100).toFixed(2)}`,
    }));
    await this.seed.transacties(this.vereisGegevens().iban, transacties);
    this.geseed.push(...transacties);
  }

  /** Logt de eerste keer in; daarna is Jan al ingelogd. */
  async opentOverzicht(): Promise<void> {
    await this.logtIn();
    await (await this.sessie()).openOverzicht();
  }

  /** Met de eigen sessie: een klant heeft de rol beheerder niet. */
  async probeertSimulatiedatumTeZetten(datum: string): Promise<void> {
    await this.logtIn();
    this.uitkomst = await (await this.sessie()).zetSimulatiedatum(datum);
  }

  async verwachtGeweigerd(): Promise<void> {
    expect(this.uitkomst, `uitkomst voor ${this.alias}`).toBe('geweigerd');
  }

  private async logtIn(): Promise<void> {
    if (this.ingelogd) return;
    const { gebruikersnaam, wachtwoord } = this.vereisGegevens();
    await (await this.sessie()).logIn(gebruikersnaam, wachtwoord);
    this.ingelogd = true;
  }

  /** S6.1: met Jans gebruikersnaam en een ander wachtwoord; Jan is daarna niet ingelogd. */
  async logtInMetWachtwoord(wachtwoord: string): Promise<void> {
    await (await this.sessie()).probeerInTeLoggen(this.vereisGegevens().gebruikersnaam, wachtwoord);
  }

  /** Jan staat nog op het inlogscherm, met deze melding. */
  async verwachtInlogfout(melding: string): Promise<void> {
    expect(await (await this.sessie()).inlogfout(), `inlogfout bij ${this.alias}`).toBe(melding);
  }

  /** S6.2: via "Uitloggen"; daarna is Jan niet meer ingelogd. */
  async logtUit(): Promise<void> {
    await (await this.sessie()).logUit();
    this.ingelogd = false;
  }

  async verwachtNietIngelogd(): Promise<void> {
    expect(await (await this.sessie()).ingelogd(), `${this.alias} nog ingelogd`).toBe(false);
  }

  /** Inloggen eindigt niet stilletjes in de oude sessie: Keycloak toont het formulier voor gebruikersnaam en wachtwoord. */
  async verwachtInlogscherm(): Promise<void> {
    expect(await (await this.sessie()).inloggenVraagtWachtwoord(), `inlogscherm voor ${this.alias}`).toBe(true);
  }

  async verwachtRekeningInOverzicht(soort: Soort, saldo: string): Promise<void> {
    const { iban } = this.vereisGegevens();
    expect(await (await this.sessie()).rekeningenInOverzicht(), `rekeningen van ${this.alias}`).toContainEqual({
      soort,
      iban,
      saldo,
    });
  }

  async opentBetaalrekening(): Promise<void> {
    await this.opentOverzicht();
    await (await this.sessie()).openRekening(this.vereisGegevens().iban);
  }

  /** Met het IBAN in het adres, zoals een bladwijzer of een gedeelde link: andermans rekening staat niet in het overzicht. */
  async opentRekening(iban: string): Promise<void> {
    await this.logtIn();
    await (await this.sessie()).openRekeningViaAdres(iban);
  }

  /** S6.1: de rekening bestaat voor deze klant niet, ook als hij van een ander is. */
  async verwachtNietGevonden(): Promise<void> {
    expect(await (await this.sessie()).rekeningNietGevonden(), `rekening niet gevonden bij ${this.alias}`).toBe(true);
  }

  /** Via de link "Overzicht" op de betaalrekening. */
  async gaatNaarOverzicht(): Promise<void> {
    await (await this.sessie()).naarOverzicht();
  }

  async gaatTerugInBrowser(): Promise<void> {
    await (await this.sessie()).terugInBrowser();
  }

  /** Een stap terug binnen de app, geen nieuwe start: wat geladen was, blijft (ADR 0012). */
  async verwachtDatDeAppNietOpnieuwLaadde(): Promise<void> {
    expect(await (await this.sessie()).opnieuwGeladen(), `app opnieuw geladen bij ${this.alias}`).toBe(false);
  }

  async herlaadt(): Promise<void> {
    await (await this.sessie()).herlaad();
  }

  async verwachtTransactielijst(verwacht: TransactiesOpDatum[]): Promise<void> {
    expect(await (await this.sessie()).transacties(), `transacties van ${this.alias}`).toEqual(verwacht);
  }

  /** De nieuwste `nieuwste` transacties uit heeftTransacties, per datum, nieuw naar oud; en of er "Toon meer" is. */
  async verwachtTransacties({ nieuwste, meer }: { nieuwste: number; meer: boolean }): Promise<void> {
    const verwacht: TransactiesOpDatum[] = [];
    for (const { datum, tegenpartij, bedrag } of this.geseed.toReversed().slice(0, nieuwste)) {
      if (verwacht.at(-1)?.datum !== datum) verwacht.push({ datum, transacties: [] });
      verwacht.at(-1)!.transacties.push({ tegenpartij, bedrag });
    }
    expect(await (await this.sessie()).transacties(), `transacties van ${this.alias}`).toEqual(verwacht);
    expect(await (await this.sessie()).heeftMeer(), `"Toon meer" bij ${this.alias}`).toBe(meer);
  }

  async toontMeer(keer: number): Promise<void> {
    for (let i = 0; i < keer; i++) await (await this.sessie()).toonMeer();
  }

  /** S7.1: vanaf de eigen betaalrekening, met de naam en het IBAN van de ontvanger; bevestigd op het controlescherm. */
  async betaalt({
    bedrag,
    aan,
    omschrijving,
    betalingskenmerk,
  }: { bedrag: string; aan: Klant; omschrijving?: string; betalingskenmerk?: string }): Promise<void> {
    await this.opentBetaalrekening();
    await (await this.sessie()).betaal({ bedrag, naam: aan.naam(), iban: aan.iban(), omschrijving, betalingskenmerk });
  }

  /** De transactie met deze tegenpartij uitklappen op de eigen betaalrekening, en alleen de genoemde details. */
  async verwachtDetailsVan(tegenpartij: string, verwacht: Partial<TransactieDetails>): Promise<void> {
    await this.klaptTransactieUit(tegenpartij);
    await this.verwachtDetails(verwacht);
  }

  /**
   * Zoals betaalt, maar de bank mag weigeren; de weigering volgt uit verwachtWeigering. Zonder `naam` de naam van de
   * ontvanger, met `naam` een andere (S7.1: de naam moet bij het rekeningnummer passen).
   */
  async probeertTeBetalen({ bedrag, aan, naam = aan.naam() }: { bedrag: string; aan: Klant; naam?: string }): Promise<void> {
    await this.opentBetaalrekening();
    await (await this.sessie()).probeerTeBetalen({ bedrag, naam, iban: aan.iban() });
  }

  /** S7.1 (CFR): dezelfde betaling, met twee keer dezelfde bevestiging; de bank boekt hem één keer. */
  async betaaltDubbel({ bedrag, aan }: { bedrag: string; aan: Klant }): Promise<void> {
    await this.opentBetaalrekening();
    await (await this.sessie()).betaalDubbel({ bedrag, naam: aan.naam(), iban: aan.iban() });
  }

  /** FO: op het betaalscherm het begin van het IBAN van de ontvanger typen, en de suggestie met dat IBAN kiezen. */
  async kiestSuggestie(aan: Klant): Promise<void> {
    await this.opentBetaalrekening();
    await (await this.sessie()).kiesSuggestie(aan.iban().slice(0, 16), aan.iban());
  }

  /** De naam en het IBAN van de ontvanger staan zo in het formulier. */
  async verwachtOntvangerIngevuld(aan: Klant): Promise<void> {
    expect(await (await this.sessie()).ontvangerInFormulier(), `ontvanger bij ${this.alias}`).toEqual({
      naam: aan.naam(),
      iban: aan.iban(),
    });
  }

  /** De eigen betaalrekening heeft precies deze ene transactie. */
  async verwachtAlleenTransactie(tegenpartij: string, bedrag: string): Promise<void> {
    await this.opentBetaalrekening();
    const alle = (await (await this.sessie()).transacties()).flatMap((dag) => dag.transacties);
    expect(alle, `transacties van ${this.alias}`).toEqual([{ tegenpartij, bedrag }]);
  }

  /** De eigen betaalrekening heeft geen enkele af- of bijschrijving. */
  async verwachtGeenTransacties(): Promise<void> {
    await this.opentBetaalrekening();
    expect(await (await this.sessie()).transacties(), `transacties van ${this.alias}`).toEqual([]);
  }

  /** De nieuwste transactie op de eigen betaalrekening, na die opnieuw te openen. */
  async verwachtBovensteTransactie(tegenpartij: string, bedrag: string): Promise<void> {
    await this.opentBetaalrekening();
    const bovenste = (await (await this.sessie()).transacties())[0]?.transacties[0];
    expect(bovenste, `bovenste transactie van ${this.alias}`).toEqual({ tegenpartij, bedrag });
  }

  /** Het saldo van de betaalrekening, zoals het overzicht het toont. */
  async verwachtSaldo(saldo: string): Promise<void> {
    await this.opentOverzicht();
    await this.verwachtRekeningInOverzicht('betaal', saldo);
  }

  /** Op de geopende betaalrekening. */
  async klaptTransactieUit(tegenpartij: string): Promise<void> {
    await this.opentBetaalrekening();
    await (await this.sessie()).klapUit(tegenpartij);
  }

  /** Alleen de genoemde velden; bij Van en Naar de naam en het IBAN (null: geen IBAN). */
  async verwachtDetails(verwacht: Partial<TransactieDetails>): Promise<void> {
    expect(await (await this.sessie()).details(), `details bij ${this.alias}`).toMatchObject(verwacht);
  }

  /** Op de geopende betaalrekening (S5.1). */
  async zoekt(opdracht: Zoekopdracht): Promise<void> {
    await this.opentBetaalrekening();
    await (await this.sessie()).zoek(opdracht);
  }

  /** De tegenpartijen in de lijst, nieuw naar oud; leeg als er niets gevonden is. */
  async verwachtGevonden(tegenpartijen: string[]): Promise<void> {
    const gevonden = (await (await this.sessie()).transacties()).flatMap((dag) => dag.transacties.map((t) => t.tegenpartij));
    expect(gevonden, `gevonden transacties van ${this.alias}`).toEqual(tegenpartijen);
  }

  async verwachtWeigering(probleemtype: string): Promise<void> {
    expect(await (await this.sessie()).weigering(), `weigering bij ${this.alias}`).toBe(probleemtype);
  }

  async opentZoeken(): Promise<void> {
    await this.opentBetaalrekening();
    await (await this.sessie()).openZoeken();
  }

  async verwachtZoekknop(tekst: string): Promise<void> {
    expect(await (await this.sessie()).zoekknop(), 'de knop bij het zoekformulier').toBe(tekst);
  }

  async zoektEnWist(opdracht: Zoekopdracht): Promise<void> {
    await (await this.sessie()).zoek(opdracht);
    await (await this.sessie()).wisZoeken();
  }

  async verwachtLeegZoekformulier(): Promise<void> {
    const leeg: Zoekformulier = { tekst: '', van: '', tm: '', type: '', richting: 'alle' };
    expect(await (await this.sessie()).zoekformulier(), 'het zoekformulier na wissen').toEqual(leeg);
  }

  private vereisGegevens(): GeseedeKlant {
    if (!this.gegevens) throw new Error(`${this.alias} bestaat nog niet: begin met een "Gegeven"-stap`);
    return this.gegevens;
  }
}

/** "2026-01-01" plus `dagen` dagen, in UTC. */
function dagNa(datum: string, dagen: number): string {
  const dag = new Date(`${datum}T00:00:00Z`);
  dag.setUTCDate(dag.getUTCDate() + dagen);
  return dag.toISOString().slice(0, 10);
}
