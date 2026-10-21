import { expect, type APIRequestContext } from '@playwright/test';
import type { BankDriver, RekeningInOverzicht, TransactiesOpDatum } from '../bank-driver';

/**
 * Spreekt BankSim aan via de publieke API-paden achter de ingress, net als de browser: inloggen via de BFF en
 * Keycloak (ADR 0008), daarna met het sessiecookie dat de request-context bewaart.
 */
export class RestDriver implements BankDriver {
  private geopend?: string;
  private geladen: Pagina['transacties'] = [];
  private volgende: string | null = null;

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
    await this.laadPagina(`/api/rekeningen/${iban}/transacties`);
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
    await this.laadPagina(`/api/rekeningen/${this.geopend}/transacties?na=${encodeURIComponent(this.volgende)}`);
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

  private async laadPagina(pad: string): Promise<void> {
    const antwoord = await this.request.get(pad);
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

/** Een pagina transacties van bank-api; `volgende` is de cursor voor "Toon meer", of null aan het eind. */
interface Pagina {
  transacties: { datum: string; tegenpartij: string; bedrag: string }[];
  volgende: string | null;
}
