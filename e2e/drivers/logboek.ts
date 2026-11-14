import type { APIRequestContext, APIResponse } from '@playwright/test';

/**
 * Wat de drivers tijdens een scenario naar het systeem sturen en terugkrijgen. De fixtures hangen het bij een fout aan
 * het rapport (ADR 0053). Dat rapport is openbaar: alles gaat eerst door maskeer.
 */
export class Logboek {
  private readonly regels: string[] = [];

  schrijf(regel: string): void {
    this.regels.push(maskeer(regel));
  }

  get leeg(): boolean {
    return this.regels.length === 0;
  }

  toString(): string {
    return this.regels.join('\n\n');
  }
}

/** Een antwoord kan groot zijn (een pagina HTML); voor het zoeken naar een fout is het begin genoeg. */
const MAXIMUM = 2_000;

const METHODES = ['get', 'post', 'put', 'patch', 'delete', 'head', 'fetch'] as const;

/**
 * Dezelfde request-context, maar elk verzoek komt in het logboek: methode, URL, de verzonden data, de status, de
 * doorverwijzing en het begin van de body. De drivers merken er niets van.
 */
export function gelogd(request: APIRequestContext, logboek: Logboek): APIRequestContext {
  return new Proxy(request, {
    get(doel, naam, ontvanger) {
      const waarde = Reflect.get(doel, naam, ontvanger);
      if (typeof naam !== 'string' || !(METHODES as readonly string[]).includes(naam)) return waarde;
      return async (url: string, opties?: { data?: unknown; form?: unknown; params?: unknown; method?: string }) => {
        const antwoord: APIResponse = await waarde.call(doel, url, opties);
        const methode = (naam === 'fetch' ? (opties?.method ?? 'GET') : naam).toUpperCase();
        const verzonden = opties?.form ?? opties?.data ?? opties?.params;
        const naar = antwoord.headers()['location'];
        // Na een doorverwijzing is de URL van het antwoord een andere dan die van het verzoek.
        const via = antwoord.url().endsWith(url) ? '' : ` (eindigde op ${antwoord.url()})`;
        logboek.schrijf([
          `${methode} ${url} → ${antwoord.status()}${naar ? ` (naar ${naar})` : ''}${via}`,
          ...(verzonden === undefined ? [] : [`verzonden: ${JSON.stringify(verzonden)}`]),
          `body: ${(await antwoord.text()).slice(0, MAXIMUM)}`,
        ].join('\n'));
        return antwoord;
      };
    },
  });
}

/**
 * Haalt weg wat niet in een openbaar rapport mag: elk JWT (zoals in tokens.ts), wachtwoorden in JSON en in
 * formulieren, en de eenmalige code van Keycloak in een URL.
 */
export function maskeer(tekst: string): string {
  return tekst
    .replace(/eyJ[\w-]+\.eyJ[\w-]+\.[\w-]*/g, '[JWT]')
    .replace(/("(?:wachtwoord|password)"\s*:\s*)"[^"]*"/gi, '$1"[gemaskeerd]"')
    .replace(/\b((?:password|code)=)[^&\s"]*/gi, '$1[gemaskeerd]');
}
