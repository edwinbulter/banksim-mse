import { randomUUID } from 'node:crypto';
import { expect, type APIRequestContext, type APIResponse } from '@playwright/test';
import type {
  BankDriver,
  Betaalopdracht,
  MaximaleLengtes,
  Overschrijfopdracht,
  Spaarmutatie,
  Partij,
  RekeningInOverzicht,
  TransactieDetails,
  TransactieType,
  TransactiesOpDatum,
  Uitkomst,
  Zoekformulier,
  Zoekopdracht,
} from '../bank-driver';
import { wijzigSimulatiedatum } from '../simulatiedatum';

/**
 * Spreekt BankSim aan via de publieke API-paden achter de ingress, net als de browser: inloggen via de BFF en
 * Keycloak (ADR 0008), daarna met het sessiecookie dat de request-context bewaart.
 */
export class RestDriver implements BankDriver {
  private geopend?: string;
  private geladen: Pagina['transacties'] = [];
  /** De lopende rente van de geopende spaarrekening (S8.1). */
  private lopend?: string;
  private volgende: string | null = null;
  private uitgeklapt?: TransactieJson;
  /** De zoekvelden van de laatste zoekopdracht; leeg na het openen van de rekening. */
  private filter = new URLSearchParams();
  private probleem?: string;
  private nietGevonden = false;

  constructor(private readonly request: APIRequestContext) {}

  async openStartpagina(): Promise<void> {
    const antwoord = await this.request.get('/');
    expect(antwoord.status(), 'GET /').toBe(200);
  }

  async versie(): Promise<string> {
    const antwoord = await this.request.get('/api/versie');
    expect(antwoord.status(), 'GET /api/versie').toBe(200);
    const body = (await antwoord.json()) as { versie: string };
    return body.versie;
  }

  /** De authorization-code-flow met cookies: BFF → inlogformulier van Keycloak → terug naar de BFF. */
  async logIn(gebruikersnaam: string, wachtwoord: string): Promise<void> {
    const loginPagina = await this.request.get('/oauth2/authorization/keycloak');
    expect(loginPagina.status(), 'inlogpagina van Keycloak').toBe(200);
    const action = /<form[^>]*id="kc-form-login"[^>]*action="([^"]+)"/.exec(await loginPagina.text())?.[1];
    expect(action, 'formulier kc-form-login op de inlogpagina').toBeTruthy();
    const antwoord = await this.request.post(action!.replaceAll('&amp;', '&'), {
      form: { username: gebruikersnaam, password: wachtwoord },
    });
    // Na een geslaagde login eindigt de redirect-keten op de startpagina van bank-web.
    expect(antwoord.url(), 'na inloggen terug op BankSim').toMatch(/^https:\/\/bank-mse\.localtest\.me\/?$/);
  }

  async probeerInTeLoggen(): Promise<void> {
    throw new Error('Het inlogscherm bestaat alleen in de browser (@ui)');
  }

  async inlogfout(): Promise<string | undefined> {
    throw new Error('Het inlogscherm bestaat alleen in de browser (@ui)');
  }

  /**
   * Zoals Angular: een POST met het CSRF-token uit het cookie XSRF-TOKEN (ADR 0016). De BFF antwoordt 202 met de
   * uitlog-URL van Keycloak in Location; die volgt de app, en dus ook de driver (ADR 0021).
   */
  async logUit(): Promise<void> {
    const antwoord = await this.request.post('/logout', { headers: await this.csrfHeader(), maxRedirects: 0 });
    expect(antwoord.status(), 'POST /logout').toBe(202);
    const keycloak = antwoord.headers()['location'];
    expect(keycloak, 'Location na POST /logout').toBeTruthy();
    const terug = await this.request.get(keycloak!);
    expect(terug.url(), 'na uitloggen terug op BankSim').toMatch(/^https:\/\/bank-mse\.localtest\.me\/?$/);
  }

  async ingelogd(): Promise<boolean> {
    const status = (await this.request.get('/api/rekeningen')).status();
    expect([200, 401], 'GET /api/rekeningen').toContain(status);
    return status === 200;
  }

  /** Na de redirects staat er een formulier kc-form-login, of de keten eindigt al op BankSim. */
  async inloggenVraagtWachtwoord(): Promise<boolean> {
    const antwoord = await this.request.get('/oauth2/authorization/keycloak');
    return /<form[^>]*id="kc-form-login"/.test(await antwoord.text());
  }

  private async csrfToken(): Promise<string | undefined> {
    return (await this.request.storageState()).cookies.find((cookie) => cookie.name === 'XSRF-TOKEN')?.value;
  }

  async openOverzicht(): Promise<void> {
    // Het overzicht is één API-aanroep; die doet rekeningenInOverzicht.
  }

  async rekeningenInOverzicht(): Promise<RekeningInOverzicht[]> {
    const antwoord = await this.request.get('/api/rekeningen');
    expect(antwoord.status(), 'GET /api/rekeningen').toBe(200);
    return (await antwoord.json()) as RekeningInOverzicht[];
  }

  /** Laadt de eerste pagina transacties, zoals de browser bij het openen van de rekening. */
  async openRekening(iban: string): Promise<void> {
    this.geopend = iban;
    this.geladen = [];
    this.filter = new URLSearchParams();
    await this.laadPagina(`/api/rekeningen/${iban}/transacties`);
  }

  /** Hetzelfde verzoek als openRekening; een 404 is hier een uitkomst, geen fout (S6.1). */
  async openRekeningViaAdres(iban: string): Promise<void> {
    this.geopend = iban;
    this.geladen = [];
    this.filter = new URLSearchParams();
    const pad = `/api/rekeningen/${iban}/transacties`;
    const antwoord = await this.request.get(pad);
    this.nietGevonden = antwoord.status() === 404;
    if (!this.nietGevonden) await this.verwerkPagina(pad, antwoord);
  }

  async rekeningNietGevonden(): Promise<boolean> {
    return this.nietGevonden;
  }

  /** De geladen pagina's achter elkaar; de groepering per datum volgt de volgorde van de API, zonder te sorteren. */
  async transacties(): Promise<TransactiesOpDatum[]> {
    const perDatum: TransactiesOpDatum[] = [];
    for (const { datum, tegenpartij, bedrag } of this.geladen) {
      if (perDatum.at(-1)?.datum !== datum) perDatum.push({ datum, transacties: [] });
      perDatum.at(-1)!.transacties.push({ tegenpartij, bedrag });
    }
    return perDatum;
  }

  async heeftMeer(): Promise<boolean> {
    return this.volgende !== null;
  }

  /** "Toon meer": de volgende pagina met de cursor uit de vorige. */
  async toonMeer(): Promise<void> {
    if (!this.geopend || this.volgende === null) throw new Error('Geen volgende pagina');
    const parameters = new URLSearchParams(this.filter);
    parameters.set('na', this.volgende);
    await this.laadPagina(`/api/rekeningen/${this.geopend}/transacties?${parameters}`);
  }

  /** De API levert de details al in de lijst; uitklappen is hier de geladen transactie kiezen. */
  async klapUit(tegenpartij: string): Promise<void> {
    this.uitgeklapt = this.geladen.find((transactie) => transactie.tegenpartij === tegenpartij);
    expect(this.uitgeklapt, `geladen transactie met tegenpartij ${tegenpartij}`).toBeDefined();
  }

  async details(): Promise<TransactieDetails> {
    const { type, tijdstip, datum, van, naar, omschrijving, betalingskenmerk } = this.uitgeklapt!;
    // "2026-10-01T06:02:00+02:00": de API geeft het tijdstip al in Europe/Amsterdam.
    return {
      transactietype: type,
      van,
      naar,
      datumTransactie: tijdstip?.slice(0, 16) ?? '',
      uitgevoerdOp: datum,
      ...(omschrijving ? { omschrijving } : {}),
      ...(betalingskenmerk ? { betalingskenmerk } : {}),
    };
  }

  /**
   * GET met de zoekvelden als parameters, letterlijk zoals de klant ze invult; "Toon meer" stuurt ze opnieuw mee.
   * Een 400 is een weigering met een probleemtype (RFC 9457).
   */
  async zoek(opdracht: Zoekopdracht): Promise<void> {
    if (!this.geopend) throw new Error('Open eerst een rekening');
    this.filter = new URLSearchParams(
      Object.entries(opdracht).filter((veld): veld is [string, string] => veld[1] !== undefined && veld[1] !== ''),
    );
    this.geladen = [];
    this.probleem = undefined;
    const pad = `/api/rekeningen/${this.geopend}/transacties?${this.filter}`;
    const antwoord = await this.request.get(pad);
    if (antwoord.status() === 400) {
      const { type } = (await antwoord.json()) as { type?: string };
      this.probleem = type?.split(':').at(-1);
      return;
    }
    await this.verwerkPagina(pad, antwoord);
  }

  async weigering(): Promise<string | undefined> {
    return this.probleem;
  }

  async openZoeken(): Promise<void> {
    throw new Error('Het zoekformulier bestaat alleen in de browser (@ui)');
  }

  async zoekknop(): Promise<string> {
    throw new Error('Het zoekformulier bestaat alleen in de browser (@ui)');
  }

  async wisZoeken(): Promise<void> {
    throw new Error('Het zoekformulier bestaat alleen in de browser (@ui)');
  }

  async zoekformulier(): Promise<Zoekformulier> {
    throw new Error('Het zoekformulier bestaat alleen in de browser (@ui)');
  }

  /** Het overzicht is één API-aanroep, die rekeningenInOverzicht doet. */
  async naarOverzicht(): Promise<void> {
    this.geopend = undefined;
  }

  async terugInBrowser(): Promise<void> {
    throw new Error('Geen browser in de REST-driver: een scenario met de terugknop hoort de tag @ui te hebben');
  }

  async opnieuwGeladen(): Promise<boolean> {
    throw new Error('Geen browser in de REST-driver: een scenario met de terugknop hoort de tag @ui te hebben');
  }

  async herlaad(): Promise<void> {
    throw new Error('Geen browser in de REST-driver: een scenario met herladen hoort de tag @ui te hebben');
  }

  /**
   * S7.1: één POST met het CSRF-token, zoals Angular na "Bevestigen" op het controlescherm. Daarna laadt de driver de
   * rekening opnieuw, zoals de app terugkeert naar de betaalrekening.
   */
  async betaal({ bedrag, naam, iban, omschrijving, betalingskenmerk }: Betaalopdracht): Promise<void> {
    const van = this.geopend;
    expect(van, 'een geopende rekening om van te betalen').toBeTruthy();
    const antwoord = await this.request.post('/api/betalingen', {
      headers: await this.betaalHeaders(),
      data: { van, naar: iban, naam, bedrag, omschrijving, betalingskenmerk },
    });
    expect(antwoord.status(), `POST /api/betalingen: ${(await antwoord.text()).slice(0, 300)}`).toBe(201);
    await this.openRekening(van!);
  }

  /** Twee keer dezelfde POST met dezelfde sleutel, zoals de app na een storing opnieuw verstuurt; allebei 201. */
  async betaalDubbel({ bedrag, naam, iban }: Betaalopdracht): Promise<void> {
    const van = this.geopend;
    expect(van, 'een geopende rekening om van te betalen').toBeTruthy();
    const verzoek = {
      headers: await this.betaalHeaders(),
      data: { van, naar: iban, naam, bedrag },
    };
    for (const keer of ['eerste', 'tweede']) {
      const antwoord = await this.request.post('/api/betalingen', verzoek);
      expect(antwoord.status(), `${keer} POST /api/betalingen: ${(await antwoord.text()).slice(0, 300)}`).toBe(201);
    }
    await this.openRekening(van!);
  }

  async openBetalen(): Promise<void> {
    throw new Error('Het betaalscherm bestaat alleen in de browser (@ui)');
  }

  async maximaleLengtes(): Promise<MaximaleLengtes> {
    throw new Error('Het betaalscherm bestaat alleen in de browser (@ui)');
  }

  async kiesSuggestie(): Promise<void> {
    throw new Error('Een suggestie kiezen bestaat alleen in de browser (@ui)');
  }

  async ontvangerInFormulier(): Promise<{ naam: string; iban: string }> {
    throw new Error('Het betaalformulier bestaat alleen in de browser (@ui)');
  }

  /** Een weigering is een 422 met een probleemtype (RFC 9457); een gelukte betaling laadt de rekening opnieuw. */
  async probeerTeBetalen({ bedrag, naam, iban }: Betaalopdracht): Promise<void> {
    const van = this.geopend;
    expect(van, 'een geopende rekening om van te betalen').toBeTruthy();
    this.probleem = undefined;
    const antwoord = await this.request.post('/api/betalingen', {
      headers: await this.betaalHeaders(),
      data: { van, naar: iban, naam, bedrag },
    });
    if (antwoord.status() === 422) {
      const { type } = (await antwoord.json()) as { type?: string };
      this.probleem = type?.split(':').at(-1);
      return;
    }
    expect(antwoord.status(), `POST /api/betalingen: ${(await antwoord.text()).slice(0, 300)}`).toBe(201);
    await this.openRekening(van!);
  }

  /** Het CSRF-token en, zoals de app per controlescherm, een nieuwe Idempotency-Key (ADR 0027). */
  private async betaalHeaders(): Promise<Record<string, string>> {
    return { ...(await this.csrfHeader()), 'Idempotency-Key': randomUUID() };
  }

  /** Het CSRF-token uit het cookie XSRF-TOKEN (ADR 0016); zonder cookie haalt een GET via de BFF het eerst op. */
  private async csrfHeader(): Promise<Record<string, string> | undefined> {
    let token = await this.csrfToken();
    if (!token) {
      await this.request.get('/api/versie');
      token = await this.csrfToken();
    }
    return token ? { 'X-XSRF-TOKEN': token } : undefined;
  }

  /**
   * Dezelfde route als de betaalrekening; de mutaties zijn de transacties van de spaarrekening. Net als de browser
   * vraagt hij ook de lopende rente op.
   */
  async openSpaarrekening(iban: string): Promise<void> {
    await this.openRekening(iban);
    const pad = `/api/rekeningen/${iban}/lopende-rente`;
    const antwoord = await this.request.get(pad);
    expect(antwoord.status(), `GET ${pad}`).toBe(200);
    this.lopend = ((await antwoord.json()) as { bedrag: string }).bedrag;
  }

  /** De frontend maakt de regels (TO); hier afgeleid uit de volgorde van de API: een jaartal bij elk ouder jaar. */
  async spaarkoppen(): Promise<string[]> {
    const koppen: string[] = [];
    let vorige: string | undefined;
    for (const { datum } of this.geladen) {
      if (datum === vorige) continue;
      if (vorige && datum.slice(0, 4) !== vorige.slice(0, 4)) koppen.push(datum.slice(0, 4));
      koppen.push(datum);
      vorige = datum;
    }
    return koppen;
  }

  async legIn(opdracht: Overschrijfopdracht): Promise<void> {
    await this.schrijfOver(opdracht);
    await this.openSpaarrekening(opdracht.naar);
  }

  async neemOp(opdracht: Overschrijfopdracht): Promise<void> {
    await this.schrijfOver(opdracht);
    await this.openSpaarrekening(opdracht.van);
  }

  /** Zoals de app: met CSRF-token en een eigen Idempotency-Key (ADR 0027). */
  private async schrijfOver({ van, naar, bedrag }: Overschrijfopdracht): Promise<void> {
    const antwoord = await this.request.post('/api/overschrijvingen', {
      headers: await this.betaalHeaders(),
      data: { van, naar, bedrag },
    });
    expect(antwoord.status(), `POST /api/overschrijvingen: ${(await antwoord.text()).slice(0, 300)}`).toBe(201);
  }

  async lopendeRente(): Promise<string> {
    if (this.lopend === undefined) throw new Error('Open eerst een spaarrekening');
    return this.lopend;
  }

  async spaarmutaties(): Promise<Spaarmutatie[]> {
    return this.geladen.map(({ datum, type, bedrag }) => ({ datum, type: SPAARTYPEN[type], bedrag }));
  }

  async zetSimulatiedatum(datum: string): Promise<Uitkomst> {
    return wijzigSimulatiedatum(this.request, async () => (await this.request.storageState()).cookies, datum);
  }

  async wisSimulatiedatum(): Promise<Uitkomst> {
    return wijzigSimulatiedatum(this.request, async () => (await this.request.storageState()).cookies, null);
  }

  private async laadPagina(pad: string): Promise<void> {
    await this.verwerkPagina(pad, await this.request.get(pad));
  }

  private async verwerkPagina(pad: string, antwoord: APIResponse): Promise<void> {
    expect(antwoord.status(), `GET ${pad}`).toBe(200);
    const pagina = (await antwoord.json()) as Pagina;
    expect(Object.keys(pagina).sort(), `GET ${pad} geeft een pagina met transacties en volgende`).toEqual([
      'transacties',
      'volgende',
    ]);
    this.geladen.push(...pagina.transacties);
    this.volgende = pagina.volgende;
  }
}

/** Het type van de API → het soort spaarmutatie (S8.1). */
const SPAARTYPEN: Record<string, Spaarmutatie['type']> = { RENTE: 'Rente', INLEG: 'Inleg', OPNAME: 'Opname' };

/** Een pagina transacties van bank-api; `volgende` is de cursor voor "Toon meer", of null aan het eind. */
interface Pagina {
  transacties: TransactieJson[];
  volgende: string | null;
}

/** Een transactie van bank-api, met de details voor het uitklappen (S4.1). */
interface TransactieJson {
  datum: string;
  tegenpartij: string;
  bedrag: string;
  type: TransactieType;
  tijdstip: string;
  van: Partij;
  naar: Partij;
  /** Null als er geen is (S7.1). */
  omschrijving: string | null;
  betalingskenmerk: string | null;
}
