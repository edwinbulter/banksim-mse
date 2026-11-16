import type { ShellDriver } from '../shell/shell-driver';

/** Leest Grafana in otel-lgtm (ADR 0054) via de service-proxy van de API-server; Grafana laat anoniem lezen toe. */
export class GrafanaDriver {
  constructor(
    private readonly shell: ShellDriver,
    private readonly namespace: string,
  ) {}

  /** De titels van de dashboards. */
  async dashboards(): Promise<string[]> {
    const resultaat = await this.shell.kubectl([
      'get', '--raw', `/api/v1/namespaces/${this.namespace}/services/otel-lgtm:3000/proxy/api/search?type=dash-db`,
    ]);
    if (resultaat.code !== 0) return [];
    return (JSON.parse(resultaat.stdout) as { title: string }[]).map((dashboard) => dashboard.title);
  }
}
