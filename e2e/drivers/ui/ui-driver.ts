import type { Page } from '@playwright/test';
import type {
  BankDriver,
  RekeningInOverzicht,
  TransactieDetails,
  TransactiesOpDatum,
  Uitkomst,
} from '../bank-driver';
import { wijzigSimulatiedatum } from '../simulatiedatum';
import { BetaalrekeningPagina } from './betaalrekening-pagina';
import { InlogPagina } from './inlog-pagina';
import { OverzichtPagina } from './overzicht-pagina';
import { StartPagina } from './start-pagina';

/** Spreekt BankSim aan via de browser, met page objects per scherm. */
export class UiDriver implements BankDriver {
  private readonly start: StartPagina;
  private readonly inlog: InlogPagina;
  private readonly overzicht: OverzichtPagina;
  private readonly betaalrekening: BetaalrekeningPagina;

  constructor(private readonly page: Page) {
    this.start = new StartPagina(page);
    this.inlog = new InlogPagina(page);
    this.overzicht = new OverzichtPagina(page);
    this.betaalrekening = new BetaalrekeningPagina(page);
  }

  async openStartpagina(): Promise<void> {
    await this.start.open();
  }

  async versie(): Promise<string> {
    return this.start.versie();
  }

  async logIn(gebruikersnaam: string, wachtwoord: string): Promise<void> {
    await this.start.open();
    await this.start.kiesInloggen();
    await this.inlog.logIn(gebruikersnaam, wachtwoord);
  }

  async openOverzicht(): Promise<void> {
    await this.overzicht.wachtTotGeladen();
  }

  async rekeningenInOverzicht(): Promise<RekeningInOverzicht[]> {
    return this.overzicht.rekeningen();
  }

  async openRekening(iban: string): Promise<void> {
    await this.betaalrekening.open(iban);
  }

  async transacties(): Promise<TransactiesOpDatum[]> {
    return this.betaalrekening.transacties();
  }

  async heeftMeer(): Promise<boolean> {
    return this.betaalrekening.heeftMeer();
  }

  async klapUit(tegenpartij: string): Promise<void> {
    await this.betaalrekening.klapUit(tegenpartij);
  }

  async details(): Promise<TransactieDetails> {
    return this.betaalrekening.details();
  }

  async toonMeer(): Promise<void> {
    await this.betaalrekening.toonMeer();
  }

  async naarOverzicht(): Promise<void> {
    await this.betaalrekening.kiesOverzicht();
    await this.overzicht.wachtTotGeladen();
  }

  /** Zet vóór de stap terug een markering op window; laadt de app opnieuw, dan is die weg. */
  async terugInBrowser(): Promise<void> {
    await this.page.evaluate(() => Object.assign(window, { banksimMarkering: true }));
    await this.page.goBack();
    await this.overzicht.wachtTotGeladen();
  }

  async opnieuwGeladen(): Promise<boolean> {
    return this.page.evaluate(() => !('banksimMarkering' in window));
  }

  async herlaad(): Promise<void> {
    await this.page.reload();
  }

  /** Er is nog geen scherm (S10.2): via de API, met de cookies van deze browser. */
  async zetSimulatiedatum(datum: string): Promise<Uitkomst> {
    return wijzigSimulatiedatum(this.page.request, () => this.page.context().cookies(), datum);
  }

  async wisSimulatiedatum(): Promise<Uitkomst> {
    return wijzigSimulatiedatum(this.page.request, () => this.page.context().cookies(), null);
  }
}
