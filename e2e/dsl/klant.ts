import { expect } from '@playwright/test';
import type { BankDriver, Soort, TransactiesOpDatum, Uitkomst } from '../drivers/bank-driver';

interface GeseedeTransactie {
  datum: string;
  tegenpartij: string;
  bedrag: string;
}
import type { BankSeed, GeseedeKlant } from '../drivers/shell/bank-seed';

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

  constructor(
    private readonly alias: string,
    private readonly driver: BankDriver,
    private readonly seed: BankSeed,
  ) {}

  async heeftBetaalrekening({ saldo }: { saldo: string }): Promise<void> {
    this.gegevens = await this.seed.klantMetBetaalrekening(this.alias, saldo);
  }

  async heeftBetaald({ op, bedrag, aan }: { op: string; bedrag: string; aan: string }): Promise<void> {
    await this.seed.transactie(this.vereisGegevens().iban, op, `-${bedrag}`, aan);
  }

  async heeftOntvangen({ op, bedrag, van }: { op: string; bedrag: string; van: string }): Promise<void> {
    await this.seed.transactie(this.vereisGegevens().iban, op, bedrag, van);
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
    await this.driver.openOverzicht();
  }

  /** Met de eigen sessie: een klant heeft de rol beheerder niet. */
  async probeertSimulatiedatumTeZetten(datum: string): Promise<void> {
    await this.logtIn();
    this.uitkomst = await this.driver.zetSimulatiedatum(datum);
  }

  async verwachtGeweigerd(): Promise<void> {
    expect(this.uitkomst, `uitkomst voor ${this.alias}`).toBe('geweigerd');
  }

  private async logtIn(): Promise<void> {
    if (this.ingelogd) return;
    const { gebruikersnaam, wachtwoord } = this.vereisGegevens();
    await this.driver.logIn(gebruikersnaam, wachtwoord);
    this.ingelogd = true;
  }

  async verwachtRekeningInOverzicht(soort: Soort, saldo: string): Promise<void> {
    const { iban } = this.vereisGegevens();
    expect(await this.driver.rekeningenInOverzicht(), `rekeningen van ${this.alias}`).toContainEqual({
      soort,
      iban,
      saldo,
    });
  }

  async opentBetaalrekening(): Promise<void> {
    await this.opentOverzicht();
    await this.driver.openRekening(this.vereisGegevens().iban);
  }

  /** Via de link "Overzicht" op de betaalrekening. */
  async gaatNaarOverzicht(): Promise<void> {
    await this.driver.naarOverzicht();
  }

  async gaatTerugInBrowser(): Promise<void> {
    await this.driver.terugInBrowser();
  }

  /** Een stap terug binnen de app, geen nieuwe start: wat geladen was, blijft (ADR 0012). */
  async verwachtDatDeAppNietOpnieuwLaadde(): Promise<void> {
    expect(await this.driver.opnieuwGeladen(), `app opnieuw geladen bij ${this.alias}`).toBe(false);
  }

  async herlaadt(): Promise<void> {
    await this.driver.herlaad();
  }

  async verwachtTransactielijst(verwacht: TransactiesOpDatum[]): Promise<void> {
    expect(await this.driver.transacties(), `transacties van ${this.alias}`).toEqual(verwacht);
  }

  /** De nieuwste `nieuwste` transacties uit heeftTransacties, per datum, nieuw naar oud; en of er "Toon meer" is. */
  async verwachtTransacties({ nieuwste, meer }: { nieuwste: number; meer: boolean }): Promise<void> {
    const verwacht: TransactiesOpDatum[] = [];
    for (const { datum, tegenpartij, bedrag } of this.geseed.toReversed().slice(0, nieuwste)) {
      if (verwacht.at(-1)?.datum !== datum) verwacht.push({ datum, transacties: [] });
      verwacht.at(-1)!.transacties.push({ tegenpartij, bedrag });
    }
    expect(await this.driver.transacties(), `transacties van ${this.alias}`).toEqual(verwacht);
    expect(await this.driver.heeftMeer(), `"Toon meer" bij ${this.alias}`).toBe(meer);
  }

  async toontMeer(keer: number): Promise<void> {
    for (let i = 0; i < keer; i++) await this.driver.toonMeer();
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
