import type { APIRequestContext } from '@playwright/test';
import type { Uitkomst } from './bank-driver';

const PAD = '/api/beheer/simulatiedatum';

/**
 * Zet of wist de simulatiedatum met de sessie van `request`, zoals Angular dat doet: met het CSRF-token uit het cookie
 * XSRF-TOKEN als header X-XSRF-TOKEN. Zonder cookie haalt een GET via de BFF het eerst op.
 */
export async function wijzigSimulatiedatum(
  request: APIRequestContext,
  cookies: () => Promise<{ name: string; value: string }[]>,
  datum: string | null,
): Promise<Uitkomst> {
  let token = (await cookies()).find((cookie) => cookie.name === 'XSRF-TOKEN')?.value;
  if (!token) {
    await request.get('/api/versie');
    token = (await cookies()).find((cookie) => cookie.name === 'XSRF-TOKEN')?.value;
  }
  const headers = token ? { 'X-XSRF-TOKEN': token } : undefined;
  const antwoord =
    datum === null
      ? await request.delete(PAD, { headers })
      : await request.put(PAD, { headers, data: { datum } });
  if (antwoord.ok()) return 'gelukt';
  if (antwoord.status() === 403) return 'geweigerd';
  const methode = datum === null ? 'DELETE' : 'PUT';
  throw new Error(`${methode} ${PAD} gaf ${antwoord.status()}: ${(await antwoord.text()).slice(0, 300)}`);
}
