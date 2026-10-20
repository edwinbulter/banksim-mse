import { randomBytes } from 'node:crypto';
import type { ShellDriver } from './shell-driver';

const NAMESPACE = 'banksim-mse';
const RELEASE = 'banksim-mse';

/** Wat bank-seed teruggeeft voor een aangemaakte klant. */
export interface GeseedeKlant {
  gebruikersnaam: string;
  wachtwoord: string;
  iban: string;
}

// Eén prefix per testrun, zodat aliassen uit verschillende runs en projecten nooit botsen (ADR 0003).
const RUN = randomBytes(3).toString('hex');
let teller = 0;

/**
 * Start bank-seed als pod in namespace banksim-mse (ADR 0003) en leest het resultaat als JSON uit de laatste regel
 * van de uitvoer. De pod krijgt zijn rechten uit het Secret bank-seed; de test zelf kent geen wachtwoorden.
 */
export class BankSeed {
  constructor(private readonly shell: ShellDriver) {}

  async klantMetBetaalrekening(alias: string, saldo: string): Promise<GeseedeKlant> {
    return this.voerUit(['klant', '--alias', uniek(alias), '--betaalrekening', saldo]);
  }

  private async voerUit(args: string[]): Promise<GeseedeKlant> {
    const tag = (await this.shell.helmWaarden(NAMESPACE, RELEASE))?.imageTag;
    if (!tag) throw new Error(`BankSim-MSE is niet geïnstalleerd in ${this.shell.context}`);
    const naam = `bank-seed-${RUN}-${++teller}-${randomBytes(2).toString('hex')}`;
    const image = `banksim-mse/bank-seed:${tag}`;
    const overrides = {
      spec: {
        containers: [{ name: naam, image, args, envFrom: [{ secretRef: { name: 'bank-seed' } }] }],
      },
    };
    const resultaat = await this.shell.kubectl([
      '-n', NAMESPACE, 'run', naam,
      `--image=${image}`, '--restart=Never', '--rm', '--attach', '--quiet',
      // Start de pod niet (bijvoorbeeld een ontbrekend image), dan snel falen in plaats van op de testtimeout.
      '--pod-running-timeout=20s',
      `--overrides=${JSON.stringify(overrides)}`,
    ]);
    const laatsteRegel = resultaat.stdout.trim().split('\n').at(-1) ?? '';
    if (resultaat.code !== 0 || !laatsteRegel.startsWith('{')) {
      // Waarom de pod niet startte (ErrImagePull, CreateContainerConfigError, …), voor een bruikbare melding.
      const reden = await this.shell.kubectl([
        '-n', NAMESPACE, 'get', 'pod', naam, '-o', 'jsonpath={.status.containerStatuses[0].state.waiting.reason}',
      ]);
      // Bij een mislukte start ruimt --rm de pod niet altijd op.
      await this.shell.kubectl(['-n', NAMESPACE, 'delete', 'pod', naam, '--ignore-not-found', '--wait=false']);
      throw new Error(`bank-seed ${args.join(' ')} faalde (${reden.stdout || 'geen reden'}):\n${resultaat.uitvoer}`);
    }
    return JSON.parse(laatsteRegel) as GeseedeKlant;
  }
}

function uniek(alias: string): string {
  return `${alias.toLowerCase()}-${RUN}-${++teller}`;
}
