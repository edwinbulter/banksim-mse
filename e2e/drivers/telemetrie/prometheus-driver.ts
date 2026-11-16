import type { ShellDriver } from '../shell/shell-driver';

interface PrometheusAntwoord {
  data: { result: { metric: Record<string, string>; value: [number, string] }[] };
}

/** Leest Prometheus in otel-lgtm (ADR 0054) via de service-proxy van de API-server, net als TempoDriver. */
export class PrometheusDriver {
  constructor(
    private readonly shell: ShellDriver,
    private readonly namespace: string,
  ) {}

  /** De uitkomst van een PromQL-query nu: per reeks de labels en de waarde. Een onbekende metric geeft geen reeksen. */
  async reeksen(query: string): Promise<{ labels: Record<string, string>; waarde: number }[]> {
    const resultaat = await this.shell.kubectl([
      'get', '--raw', `/api/v1/namespaces/${this.namespace}/services/otel-lgtm:9090/proxy/api/v1/query?${new URLSearchParams({ query })}`,
    ]);
    if (resultaat.code !== 0) return [];
    return (JSON.parse(resultaat.stdout) as PrometheusAntwoord).data.result.map((reeks) => ({
      labels: reeks.metric,
      waarde: Number(reeks.value[1]),
    }));
  }
}
