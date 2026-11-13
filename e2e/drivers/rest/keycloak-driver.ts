import { createHash, randomBytes, randomUUID } from 'node:crypto';
import type { APIRequestContext } from '@playwright/test';

export type PkceMethode = 'S256' | 'plain';

/** Een loginaanvraag zoals de BFF hem doet; zonder `pkce` ontbreken code_challenge en code_challenge_method. */
export interface Loginaanvraag {
  realm: string;
  clientId: string;
  redirectUri: string;
  pkce?: PkceMethode;
}

/** Wat Keycloak terugstuurde: het inlogscherm, of een doorverwijzing (Location), of een foutpagina. */
export interface Loginantwoord {
  status: number;
  inlogscherm: boolean;
  doorverwijzing?: string;
}

/**
 * Het inlogadres van Keycloak (authorization endpoint, OAuth 2.0), rechtstreeks zoals de browser het opvraagt. Volgt
 * geen doorverwijzing: de driver geeft terug waar Keycloak heen stuurt, de DSL beweert of dat mag.
 */
export class KeycloakDriver {
  constructor(
    private readonly request: APIRequestContext,
    private readonly authUrl: string,
  ) {}

  async vraagLoginAan({ realm, clientId, redirectUri, pkce }: Loginaanvraag): Promise<Loginantwoord> {
    const params: Record<string, string> = {
      client_id: clientId,
      response_type: 'code',
      scope: 'openid',
      redirect_uri: redirectUri,
      state: randomUUID(),
    };
    if (pkce) {
      // RFC 7636: een willekeurige code_verifier; bij S256 gaat alleen de hash ervan mee, bij plain de verifier zelf.
      const verifier = randomBytes(32).toString('base64url');
      params.code_challenge = pkce === 'S256' ? createHash('sha256').update(verifier).digest('base64url') : verifier;
      params.code_challenge_method = pkce;
    }
    const antwoord = await this.request.get(`${this.authUrl}/realms/${realm}/protocol/openid-connect/auth`, {
      params,
      maxRedirects: 0,
    });
    // Alleen het inlogscherm heeft dit formulier; de foutpagina van Keycloak heeft dezelfde titel, maar geen formulier.
    const inlogscherm = antwoord.status() === 200 && (await antwoord.text()).includes('id="kc-form-login"');
    return { status: antwoord.status(), inlogscherm, doorverwijzing: antwoord.headers()['location'] };
  }
}
