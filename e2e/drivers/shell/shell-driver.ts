import { type ChildProcessWithoutNullStreams, execFile, spawn } from 'node:child_process';
import { mkdtemp, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

export interface Resultaat {
  code: number;
  /** stdout, voor JSON. */
  stdout: string;
  /** stdout en stderr samen, voor meldingen. */
  uitvoer: string;
}

/** Een Kubernetes-object, met alleen de velden die de DSL vergelijkt. */
export interface K8sObject {
  kind: string;
  metadata: { name: string; namespace?: string; uid: string; generation?: number };
  spec?: unknown;
  data?: unknown;
}

const REPO_ROOT = fileURLToPath(new URL('../../../', import.meta.url));
// De context die de scripts gebruiken als je geen --context meegeeft (ADR 0004).
const STANDAARD_CONTEXT = 'kind-single-node';
// De images die install.sh laadt (deploy/scripts/install.sh, load_images).
const IMAGES = ['bank-api', 'bank-bff', 'bank-seed', 'bank-web'].map((naam) => `banksim-mse/${naam}`);

/**
 * Spreekt de omgeving aan zoals een beheerder dat doet: met de installatiescripts, kubectl, helm en curl.
 * Geen shell-interpolatie: elk argument gaat los naar execFile.
 */
export class ShellDriver {
  constructor(readonly context: string) {}

  /** deploy/scripts/<naam> met de gegeven argumenten. Faalt niet bij een exitcode ≠ 0; dat beoordeelt de DSL. */
  script(naam: string, args: string[], env: Record<string, string> = {}): Promise<Resultaat> {
    return voerUit(join(REPO_ROOT, 'deploy/scripts', naam), args, env);
  }

  /** Tagt de images van BankSim-MSE in Docker opnieuw, zodat install.sh --image-tag ze als nieuwe versie laadt. */
  async tagImages(van: string, naar: string): Promise<void> {
    for (const image of IMAGES) {
      await verwacht(voerUit('docker', ['tag', `${image}:${van}`, `${image}:${naar}`]));
    }
  }

  async kubectl(args: string[]): Promise<Resultaat> {
    return voerUit('kubectl', ['--context', this.context, ...args]);
  }

  /** kubectl als langlopend proces, met stdin open; voor de seed-sessie (ADR 0020). */
  kubectlProces(args: string[]): ChildProcessWithoutNullStreams {
    return spawn('kubectl', ['--context', this.context, ...args]);
  }

  /** kubectl get … -o json; geeft de items terug. */
  async objecten(args: string[]): Promise<K8sObject[]> {
    const resultaat = await this.kubectl(['get', ...args, '-o', 'json']);
    if (resultaat.code !== 0) throw new Error(`kubectl get ${args.join(' ')} faalde:\n${resultaat.uitvoer}`);
    return (JSON.parse(resultaat.stdout) as { items: K8sObject[] }).items;
  }

  async bestaat(soort: string, naam: string): Promise<boolean> {
    return (await this.kubectl(['get', soort, naam, '-o', 'name'])).code === 0;
  }

  /** De waarden van een Helm-release, of undefined als de release niet bestaat. */
  async helmWaarden(namespace: string, release: string): Promise<Record<string, unknown> | undefined> {
    const resultaat = await voerUit('helm', [
      '--kube-context', this.context, '-n', namespace, 'get', 'values', release, '-o', 'json',
    ]);
    return resultaat.code === 0 ? (JSON.parse(resultaat.stdout) as Record<string, unknown>) : undefined;
  }

  async helmRevisie(namespace: string, release: string): Promise<number> {
    const resultaat = await voerUit('helm', ['--kube-context', this.context, '-n', namespace, 'history', release, '-o', 'json']);
    if (resultaat.code !== 0) throw new Error(`helm history faalde:\n${resultaat.uitvoer}`);
    const historie = JSON.parse(resultaat.stdout) as { revision: number }[];
    return Math.max(...historie.map((r) => r.revision));
  }

  /** HTTP-status van een URL via de ingress, met het zelfondertekende certificaat van ingress-nginx. */
  async httpStatus(url: string): Promise<number> {
    const resultaat = await voerUit('curl', ['-sk', '-o', '/dev/null', '-w', '%{http_code}', '--max-time', '10', url]);
    return Number(resultaat.stdout.trim()) || 0;
  }

  /**
   * Voert `werk` uit met een tijdelijke kubeconfig waarin `actief` de actieve context is en naar een onbereikbaar
   * cluster wijst. De context van deze omgeving heet daarin kind-single-node, zodat een script zonder --context
   * hier installeert, ook in CI waar het cluster anders heet. De kubeconfig bevat credentials en wordt na afloop
   * verwijderd.
   */
  async metActieveContext<T>(actief: string, werk: (kubeconfig: string) => Promise<T>): Promise<T> {
    const map = await mkdtemp(join(tmpdir(), 'banksim-mse-kubeconfig-'));
    try {
      const pad = join(map, 'config');
      const huidig = await voerUit('kubectl', ['config', 'view', '--raw']);
      if (huidig.code !== 0) throw new Error(`kubectl config view faalde:\n${huidig.uitvoer}`);
      await writeFile(pad, huidig.stdout, { mode: 0o600 });
      const config = (...args: string[]) => voerUit('kubectl', ['--kubeconfig', pad, 'config', ...args]);
      if (this.context !== STANDAARD_CONTEXT) {
        await verwacht(config('delete-context', STANDAARD_CONTEXT), true);
        await verwacht(config('rename-context', this.context, STANDAARD_CONTEXT));
      }
      await verwacht(config('set-cluster', 'onbereikbaar', '--server=https://127.0.0.1:1'));
      // Met credentials, anders vraagt kubectl interactief om een gebruikersnaam.
      await verwacht(config('set-credentials', 'onbereikbaar', '--token=onbereikbaar'));
      await verwacht(config('set-context', actief, '--cluster=onbereikbaar', '--user=onbereikbaar'));
      await verwacht(config('use-context', actief));
      return await werk(pad);
    } finally {
      await rm(map, { recursive: true, force: true });
    }
  }
}

function voerUit(bestand: string, args: string[], env: Record<string, string> = {}): Promise<Resultaat> {
  return new Promise((resolve) => {
    const proces = execFile(
      bestand,
      args,
      { env: { ...process.env, ...env }, maxBuffer: 64 * 1024 * 1024, timeout: 10 * 60_000 },
      (fout, stdout, stderr) => {
        const code = fout ? (typeof fout.code === 'number' ? fout.code : 1) : 0;
        resolve({ code, stdout, uitvoer: `${stdout}${stderr}` });
      },
    );
    // Geen invoer: een commando dat iets vraagt, faalt dan in plaats van te blijven wachten.
    proces.stdin?.end();
  });
}

async function verwacht(resultaat: Promise<Resultaat>, magFalen = false): Promise<void> {
  const { code, uitvoer } = await resultaat;
  if (code !== 0 && !magFalen) throw new Error(uitvoer);
}
