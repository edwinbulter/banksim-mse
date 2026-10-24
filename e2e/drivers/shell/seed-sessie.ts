import { randomBytes } from 'node:crypto';
import type { ChildProcessWithoutNullStreams } from 'node:child_process';
import type { ShellDriver } from './shell-driver';

const PREFIX = 'BANKSIM-SEED ';

interface Wachtend {
  args: string[];
  resolve: (regel: string) => void;
  reject: (fout: Error) => void;
}

/**
 * Eén bank-seed-pod per Playwright-worker (ADR 0020), gestart bij de eerste opdracht. Elke opdracht gaat als één regel
 * JSON naar stdin; het antwoord is de eerstvolgende regel met de prefix BANKSIM-SEED. Bij het sluiten gaat stdin dicht,
 * de sessie stopt en `--rm` ruimt de pod op.
 */
export class SeedSessie {
  private proces?: ChildProcessWithoutNullStreams;
  private wachtend: Wachtend[] = [];
  private gelezen = '';
  /** Alle uitvoer van kubectl en de pod, voor een bruikbare melding als de sessie stopt. */
  private uitvoer = '';
  private gestopt?: Promise<void>;
  private naam = '';

  constructor(
    private readonly shell: ShellDriver,
    private readonly namespace: string,
  ) {}

  /** Stuurt de opdracht en geeft de JSON van het antwoord terug; `{"fout":…}` wordt een Error. */
  async voerUit<T>(image: string, args: string[]): Promise<T> {
    const proces = this.proces ?? this.start(image);
    const regel = await new Promise<string>((resolve, reject) => {
      this.wachtend.push({ args, resolve, reject });
      proces.stdin.write(`${JSON.stringify(args)}\n`);
    });
    const antwoord = JSON.parse(regel) as T & { fout?: string };
    if (antwoord.fout !== undefined) throw new Error(`bank-seed ${kort(args)} faalde: ${antwoord.fout}`);
    return antwoord;
  }

  /** Sluit stdin en wacht tot kubectl gestopt is; doet niets als de sessie nooit startte. */
  async sluit(): Promise<void> {
    if (!this.proces) return;
    this.proces.stdin.end();
    await this.gestopt;
  }

  private start(image: string): ChildProcessWithoutNullStreams {
    this.naam = `bank-seed-sessie-${randomBytes(4).toString('hex')}`;
    const overrides = {
      spec: {
        containers: [{
          name: this.naam, image, args: ['sessie'], stdin: true, stdinOnce: true,
          envFrom: [{ secretRef: { name: 'bank-seed' } }],
        }],
      },
    };
    const proces = this.shell.kubectlProces([
      '-n', this.namespace, 'run', this.naam, `--image=${image}`,
      '--restart=Never', '--rm', '-i', '--quiet',
      // Start de pod niet (bijvoorbeeld een ontbrekend image), dan snel falen in plaats van op de testtimeout.
      '--pod-running-timeout=20s',
      `--overrides=${JSON.stringify(overrides)}`,
    ]);
    proces.stdout.setEncoding('utf8').on('data', (stuk: string) => this.lees(stuk));
    proces.stderr.setEncoding('utf8').on('data', (stuk: string) => (this.uitvoer += stuk));
    this.gestopt = new Promise((resolve) => {
      proces.on('close', (code) => {
        this.stop(new Error(`bank-seed-sessie ${this.naam} stopte (exitcode ${code}):\n${this.uitvoer}`));
        resolve();
      });
    });
    this.proces = proces;
    return proces;
  }

  /** Antwoorden komen in volgorde; logregels zonder prefix gaan alleen naar de uitvoer. */
  private lees(stuk: string): void {
    this.gelezen += stuk;
    let einde: number;
    while ((einde = this.gelezen.indexOf('\n')) >= 0) {
      const regel = this.gelezen.slice(0, einde);
      this.gelezen = this.gelezen.slice(einde + 1);
      if (regel.startsWith(PREFIX)) this.wachtend.shift()?.resolve(regel.slice(PREFIX.length));
      else this.uitvoer += `${regel}\n`;
    }
  }

  private stop(fout: Error): void {
    for (const { reject } of this.wachtend.splice(0)) reject(fout);
    this.proces = undefined;
  }
}

/** De eerste argumenten, zodat 120 transacties de foutmelding niet onleesbaar maken. */
export function kort(args: string[]): string {
  return args.length <= 8 ? args.join(' ') : `${args.slice(0, 8).join(' ')} … (${args.length} argumenten)`;
}
