import { expect, type APIRequestContext } from '@playwright/test';
import type { BankDriver, RekeningInOverzicht } from '../bank-driver';

/**
 * Spreekt BankSim aan via de publieke API-paden achter de ingress, net als de browser: inloggen via de BFF en
 * Keycloak (ADR 0008), daarna met het sessiecookie dat de request-context bewaart.
 */
export class RestDriver implements BankDriver {
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
}
