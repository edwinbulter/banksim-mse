import type { ShellDriver } from '../shell/shell-driver';

/** Eén span uit Tempo: de dienst die hem maakte, zijn naam en zijn attributen als tekst. */
export interface Span {
  dienst: string;
  naam: string;
  attributen: Record<string, string>;
}

interface TempoAttribuut {
  key: string;
  value: Record<string, unknown>;
}

interface TempoTrace {
  batches: {
    resource: { attributes: TempoAttribuut[] };
    scopeSpans: { spans: { name: string; attributes?: TempoAttribuut[] }[] }[];
  }[];
}

/**
 * Hoe lang Tempo mag doen over een nieuwe trace. Een trace is pas te zoeken als Tempo hem afgesloten heeft: lokaal
 * gemeten 31 s na de betaling (ADR 0054). De ruimte erboven is voor een tragere runner in CI.
 */
const WACHTTIJD_MS = 90_000;
const INTERVAL_MS = 1_000;

/**
 * Leest Tempo in otel-lgtm (ADR 0054) via de service-proxy van de API-server. Er is dus geen port-forward en geen
 * eigen host nodig, alleen kubectl met de context van de shell-driver.
 */
export class TempoDriver {
  constructor(
    private readonly shell: ShellDriver,
    private readonly namespace: string,
  ) {}

  /** De traces met een span waarop `attribuut` deze waarde heeft; wacht tot Tempo er minstens één heeft. */
  async tracesMet(attribuut: string, waarde: string): Promise<Span[][]> {
    const query = `{ span.${attribuut} = "${waarde}" }`;
    const einde = Date.now() + WACHTTIJD_MS;
    let ids: string[] = [];
    while (ids.length === 0 && Date.now() < einde) {
      const gevonden = await this.vraag<{ traces?: { traceID: string }[] }>(`api/search?q=${encodeURIComponent(query)}`);
      ids = (gevonden?.traces ?? []).map((trace) => trace.traceID);
      if (ids.length === 0) await new Promise((klaar) => setTimeout(klaar, INTERVAL_MS));
    }
    // Spans van dezelfde trace kunnen later binnenkomen dan de eerste; even wachten geeft de hele trace.
    await new Promise((klaar) => setTimeout(klaar, 5 * INTERVAL_MS));
    return Promise.all(ids.map(async (id) => spansVan(await this.vraag<TempoTrace>(`api/traces/${id}`))));
  }

  private async vraag<T>(pad: string): Promise<T | undefined> {
    const resultaat = await this.shell.kubectl([
      'get', '--raw', `/api/v1/namespaces/${this.namespace}/services/otel-lgtm:3200/proxy/${pad}`,
    ]);
    return resultaat.code === 0 ? (JSON.parse(resultaat.stdout) as T) : undefined;
  }
}

function spansVan(trace: TempoTrace | undefined): Span[] {
  return (trace?.batches ?? []).flatMap((batch) => {
    const dienst = tekst(batch.resource.attributes.find((a) => a.key === 'service.name'));
    return batch.scopeSpans.flatMap((scope) =>
      scope.spans.map((span) => ({
        dienst,
        naam: span.name,
        attributen: Object.fromEntries((span.attributes ?? []).map((a) => [a.key, tekst(a)])),
      })),
    );
  });
}

function tekst(attribuut: TempoAttribuut | undefined): string {
  return attribuut ? String(Object.values(attribuut.value)[0]) : '';
}
