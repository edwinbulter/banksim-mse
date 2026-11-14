import type { GevondenToken } from './bank-driver';

/** Keycloak geeft elk token uit als JWT: drie delen in base64url, waarvan de eerste twee JSON zijn en dus met eyJ beginnen. */
const JWT = /eyJ[\w-]+\.eyJ[\w-]+\.[\w-]*/g;

/** Elk JWT in de tekst, met de claims die zeggen wat voor token het is. */
export function tokensIn(tekst: string, waar: string): GevondenToken[] {
  return [...tekst.matchAll(JWT)].map(([jwt]) => {
    const claims = leesClaims(jwt);
    if (!claims) return { soort: 'onbekend', audience: [], waar };
    const aud = claims.aud ?? [];
    return {
      soort: String(claims.typ ?? 'onbekend'),
      gebruiker: claims.preferred_username,
      audience: Array.isArray(aud) ? aud : [aud],
      waar,
    };
  });
}

function leesClaims(jwt: string): { typ?: string; aud?: string | string[]; preferred_username?: string } | undefined {
  try {
    return JSON.parse(Buffer.from(jwt.split('.')[1]!, 'base64url').toString('utf8'));
  } catch (fout) {
    if (fout instanceof SyntaxError) return undefined;
    throw fout;
  }
}
