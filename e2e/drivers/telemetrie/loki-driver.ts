import type { ShellDriver } from '../shell/shell-driver';

interface LokiAntwoord {
  data: { result: { stream: Record<string, string>; values: [string, string][] }[] };
}

/** Een logregel uit Loki: de dienst en de tekst. */
export interface Logregel {
  dienst: string;
  tekst: string;
}

/** Zoals bij Tempo: de apps en de Collector versturen in batches. */
const WACHTTIJD_MS = 60_000;
const INTERVAL_MS = 1_000;

/** Leest Loki in otel-lgtm (ADR 0054) via de service-proxy van de API-server, net als TempoDriver. */
export class LokiDriver {
  constructor(
    private readonly shell: ShellDriver,
    private readonly namespace: string,
  ) {}

  /** De regels van `dienst` bij deze trace; wacht tot er minstens één is. */
  async regelsBijTrace(dienst: string, traceId: string, sinds: Date): Promise<Logregel[]> {
    const einde = Date.now() + WACHTTIJD_MS;
    let regels: Logregel[] = [];
    while (regels.length === 0 && Date.now() < einde) {
      regels = await this.regels(`{service_name="${dienst}"} | trace_id="${traceId}"`, sinds);
      if (regels.length === 0) await new Promise((klaar) => setTimeout(klaar, INTERVAL_MS));
    }
    return regels;
  }

  /** Alle regels van alle diensten sinds `sinds` waarin `tekst` voorkomt; wacht niet. */
  async regelsMet(tekst: string, sinds: Date): Promise<Logregel[]> {
    return this.regels(`{service_name=~".+"} |= ${JSON.stringify(tekst)}`, sinds);
  }

  private async regels(query: string, sinds: Date): Promise<Logregel[]> {
    const parameters = new URLSearchParams({ query, start: `${sinds.getTime()}000000`, limit: '1000' });
    const resultaat = await this.shell.kubectl([
      'get', '--raw', `/api/v1/namespaces/${this.namespace}/services/otel-lgtm:3100/proxy/loki/api/v1/query_range?${parameters}`,
    ]);
    if (resultaat.code !== 0) return [];
    return (JSON.parse(resultaat.stdout) as LokiAntwoord).data.result.flatMap((stroom) =>
      stroom.values.map(([, tekst]) => ({ dienst: stroom.stream.service_name ?? '', tekst })),
    );
  }
}
