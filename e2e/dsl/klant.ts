import { expect } from '@playwright/test';
import type { BankDriver, Soort, TransactiesOpDatum } from '../drivers/bank-driver';
import type { BankSeed, GeseedeKlant } from '../drivers/shell/bank-seed';

/**
 * Een rekeninghouder, aangesproken met een alias ("Jan"). De seed maakt per alias een unieke klant aan (ADR 0003),
 * zodat scenario's elkaars data niet zien.
 */
export class Klant {
  private gegevens?: GeseedeKlant;

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

  async opentOverzicht(): Promise<void> {
    const { gebruikersnaam, wachtwoord } = this.vereisGegevens();
    await this.driver.logIn(gebruikersnaam, wachtwoord);
    await this.driver.openOverzicht();
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

  async verwachtTransactielijst(verwacht: TransactiesOpDatum[]): Promise<void> {
    expect(await this.driver.transacties(), `transacties van ${this.alias}`).toEqual(verwacht);
  }

  private vereisGegevens(): GeseedeKlant {
    if (!this.gegevens) throw new Error(`${this.alias} bestaat nog niet: begin met een "Gegeven"-stap`);
    return this.gegevens;
  }
}
