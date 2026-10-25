import type { Page } from '@playwright/test';
import type {
  BankDriver,
  Betaalopdracht,
  MaximaleLengtes,
  Overschrijfopdracht,
  Spaarmutatie,
  RekeningInOverzicht,
  TransactieDetails,
  TransactiesOpDatum,
  Uitkomst,
  Zoekformulier,
  Zoekopdracht,
} from '../bank-driver';
import { wijzigSimulatiedatum } from '../simulatiedatum';
import { BetaalrekeningPagina } from './betaalrekening-pagina';
import { BetalenPagina } from './betalen-pagina';
import { InlogPagina } from './inlog-pagina';
import { OverzichtPagina } from './overzicht-pagina';
import { SpaarrekeningPagina } from './spaarrekening-pagina';
import { StartPagina } from './start-pagina';
import { ZoekenPagina } from './zoeken-pagina';

/** Spreekt BankSim aan via de browser, met page objects per scherm. */
export class UiDriver implements BankDriver {
  private readonly start: StartPagina;
  private readonly inlog: InlogPagina;
  private readonly overzicht: OverzichtPagina;
  private readonly betaalrekening: BetaalrekeningPagina;
  private readonly zoeken: ZoekenPagina;
  private readonly betalen: BetalenPagina;
  private readonly spaarrekening: SpaarrekeningPagina;
  private laatsteFormulier: 'zoeken' | 'betalen' = 'zoeken';

  constructor(private readonly page: Page) {
    this.start = new StartPagina(page);
    this.inlog = new InlogPagina(page);
    this.overzicht = new OverzichtPagina(page);
    this.betaalrekening = new BetaalrekeningPagina(page);
    this.zoeken = new ZoekenPagina(page);
    this.betalen = new BetalenPagina(page);
    this.spaarrekening = new SpaarrekeningPagina(page);
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

  async probeerInTeLoggen(gebruikersnaam: string, wachtwoord: string): Promise<void> {
    await this.start.open();
    await this.start.kiesInloggen();
    await this.inlog.probeer(gebruikersnaam, wachtwoord);
  }

  async inlogfout(): Promise<string | undefined> {
    return this.inlog.fout();
  }

  async logUit(): Promise<void> {
    await this.start.kiesUitloggen();
  }

  async ingelogd(): Promise<boolean> {
    return this.start.ingelogd();
  }

  async inloggenVraagtWachtwoord(): Promise<boolean> {
    await this.start.kiesInloggen();
    return this.inlog.vraagtWachtwoord();
  }

  async openOverzicht(): Promise<void> {
    await this.overzicht.open();
  }

  async rekeningenInOverzicht(): Promise<RekeningInOverzicht[]> {
    return this.overzicht.rekeningen();
  }

  async openRekening(iban: string): Promise<void> {
    await this.betaalrekening.open(iban);
  }

  async openRekeningViaAdres(iban: string): Promise<void> {
    await this.betaalrekening.openViaAdres(iban);
  }

  async rekeningNietGevonden(): Promise<boolean> {
    return this.betaalrekening.nietGevonden();
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

  async zoek(opdracht: Zoekopdracht): Promise<void> {
    this.laatsteFormulier = 'zoeken';
    await this.zoeken.zoek(opdracht);
  }

  /** De melding van het formulier dat het laatst is verstuurd. */
  async weigering(): Promise<string | undefined> {
    return this.laatsteFormulier === 'betalen' ? this.betalen.weigering() : this.zoeken.weigering();
  }

  async openZoeken(): Promise<void> {
    await this.zoeken.open();
  }

  async zoekknop(): Promise<string> {
    return this.zoeken.knop();
  }

  async wisZoeken(): Promise<void> {
    await this.zoeken.wis();
  }

  async zoekformulier(): Promise<Zoekformulier> {
    return this.zoeken.inhoud();
  }

  async toonMeer(): Promise<void> {
    await this.betaalrekening.toonMeer();
  }

  async betaal(opdracht: Betaalopdracht): Promise<void> {
    await this.betalen.betaal(opdracht);
  }

  async betaalDubbel(opdracht: Betaalopdracht): Promise<void> {
    await this.betalen.betaalDubbel(opdracht);
  }

  async openBetalen(): Promise<void> {
    await this.betalen.open();
  }

  async maximaleLengtes(): Promise<MaximaleLengtes> {
    return this.betalen.maximaleLengtes();
  }

  async kiesSuggestie(tekst: string, iban: string): Promise<void> {
    await this.betalen.kiesSuggestie(tekst, iban);
  }

  async ontvangerInFormulier(): Promise<{ naam: string; iban: string }> {
    return this.betalen.ontvanger();
  }

  async probeerTeBetalen(opdracht: Betaalopdracht): Promise<void> {
    this.laatsteFormulier = 'betalen';
    await this.betalen.probeer(opdracht);
  }

  async openSpaarrekening(iban: string): Promise<void> {
    await this.spaarrekening.open(iban);
  }

  async spaarmutaties(): Promise<Spaarmutatie[]> {
    return this.spaarrekening.mutaties();
  }

  async spaarkoppen(): Promise<string[]> {
    return this.spaarrekening.koppen();
  }

  async legIn({ bedrag }: Overschrijfopdracht): Promise<void> {
    await this.spaarrekening.schrijfOver('Inleggen', bedrag);
  }

  async neemOp({ bedrag }: Overschrijfopdracht): Promise<void> {
    await this.spaarrekening.schrijfOver('Opnemen', bedrag);
  }

  async lopendeRente(): Promise<string> {
    return this.spaarrekening.lopendeRente();
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
