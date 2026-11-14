import { expect } from '@playwright/test';
import type { KeycloakDriver, Loginantwoord, PkceMethode } from '../drivers/rest/keycloak-driver';

const REALM = 'banksim-mse';
const CLIENT = 'bank-bff';

/**
 * Keycloak zoals een browser het ziet bij het begin van een login voor client bank-bff (ADR 0008): het inlogscherm,
 * of een weigering. De beweringen staan hier; de driver vraagt alleen aan.
 */
export class Keycloak {
  private laatste?: Loginantwoord;

  constructor(private readonly driver: KeycloakDriver) {}

  /** Zonder `pkce` ontbreekt de code_challenge. */
  async vraagtLoginAan({
    pkce,
    redirectUri,
    implicit = false,
  }: {
    pkce?: PkceMethode;
    redirectUri: string;
    /** De implicit flow (response_type=token): het token direct naar de browser. */
    implicit?: boolean;
  }): Promise<void> {
    const responseType = implicit ? 'token' : 'code';
    this.laatste = await this.driver.vraagLoginAan({ realm: REALM, clientId: CLIENT, redirectUri, pkce, responseType });
  }

  async verwachtInlogscherm(): Promise<void> {
    const antwoord = this.vereisAntwoord();
    expect(antwoord.status, `status van de loginaanvraag (Location: ${antwoord.doorverwijzing})`).toBe(200);
    expect(antwoord.inlogscherm, 'het inlogformulier van Keycloak').toBe(true);
  }

  async verwachtGeenInlogscherm(): Promise<void> {
    expect(this.vereisAntwoord().inlogscherm, 'het inlogformulier van Keycloak').toBe(false);
  }

  /**
   * OAuth 2.0 (RFC 6749): bij een geldige redirect-URI meldt Keycloak de fout daar, als parameter error. Bij de code
   * flow in de query (4.1.2.1), bij de implicit flow in het fragment (4.2.2.1).
   */
  async verwachtTeruggestuurdMetFout(redirectUri: string, fout: string): Promise<void> {
    const { status, doorverwijzing } = this.vereisAntwoord();
    expect(status, 'status van de loginaanvraag').toBe(302);
    const naar = new URL(doorverwijzing ?? '');
    expect(`${naar.origin}${naar.pathname}`, 'doorverwijzing van Keycloak').toBe(redirectUri);
    const params = naar.hash ? new URLSearchParams(naar.hash.slice(1)) : naar.searchParams;
    expect(params.get('error'), `fout in de doorverwijzing (${params.get('error_description')})`).toBe(fout);
  }

  async verwachtNietDoorgestuurdNaar(host: string): Promise<void> {
    const { doorverwijzing } = this.vereisAntwoord();
    const naar = doorverwijzing ? new URL(doorverwijzing, 'https://auth-mse.localtest.me').hostname : undefined;
    expect(naar, 'doorverwijzing van Keycloak').not.toBe(host);
  }

  private vereisAntwoord(): Loginantwoord {
    if (!this.laatste) throw new Error('Nog geen loginaanvraag: begin met een "Als"-stap');
    return this.laatste;
  }
}
