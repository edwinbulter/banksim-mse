import { expect, type APIRequestContext } from '@playwright/test';
import type { BankDriver, RekeningInOverzicht, TransactiesOpDatum } from '../bank-driver';

/**
 * Spreekt BankSim aan via de publieke API-paden achter de ingress, net als de browser: inloggen via de BFF en
 * Keycloak (ADR 0008), daarna met het sessiecookie dat de request-context bewaart.
 */
export class RestDriver implements BankDriver {
  private geopend?: string;

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

  async openRekening(iban: string): Promise<void> {
    this.geopend = iban;
  }

  /** De API geeft een platte lijst; de groepering per datum volgt de volgorde van de API, zonder te sorteren. */
  async transacties(): Promise<TransactiesOpDatum[]> {
    if (!this.geopend) throw new Error('Geen rekening geopend');
    const pad = `/api/rekeningen/${this.geopend}/transacties`;
    const antwoord = await this.request.get(pad);
    expect(antwoord.status(), `GET ${pad}`).toBe(200);
    const lijst = (await antwoord.json()) as { datum: string; tegenpartij: string; bedrag: string }[];
    const perDatum: TransactiesOpDatum[] = [];
    for (const { datum, tegenpartij, bedrag } of lijst) {
      if (perDatum.at(-1)?.datum !== datum) perDatum.push({ datum, transacties: [] });
      perDatum.at(-1)!.transacties.push({ tegenpartij, bedrag });
    }
    return perDatum;
  }
}
