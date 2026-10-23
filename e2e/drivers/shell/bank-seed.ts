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

/** Wat bank-seed teruggeeft voor een aangemaakte beheerder. */
export interface GeseedeBeheerder {
  gebruikersnaam: string;
  wachtwoord: string;
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
    return this.voerUit<GeseedeKlant>(['klant', '--alias', uniek(alias), '--betaalrekening', saldo]);
  }

  /** Een login met de realm-rol beheerder, zonder rekeningen. */
  async beheerder(alias: string): Promise<GeseedeBeheerder> {
    return this.voerUit<GeseedeBeheerder>(['beheerder', '--alias', uniek(alias)]);
  }

  /** Een geboekte transactie op de rekening; een negatief bedrag is een afschrijving. */
  async transactie(iban: string, datum: string, bedrag: string, tegenpartij: string): Promise<void> {
    await this.voerUit(['transactie', '--iban', iban, '--datum', datum, '--bedrag', bedrag, '--tegenpartij', tegenpartij]);
  }

  /**
   * Meerdere transacties in één pod, in deze volgorde geboekt; een pod per transactie zou per stuk seconden kosten.
   * Per transactie één argument "datum,bedrag,tegenpartij"; de tegenpartij mag zelf komma's bevatten.
   */
  async transacties(iban: string, transacties: { datum: string; bedrag: string; tegenpartij: string }[]): Promise<void> {
    await this.voerUit([
      'transacties', '--iban', iban,
      ...transacties.flatMap(({ datum, bedrag, tegenpartij }) => ['--transactie', `${datum},${bedrag},${tegenpartij}`]),
    ]);
  }

  private async voerUit<T>(args: string[]): Promise<T> {
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
      throw new Error(`bank-seed ${kort(args)} faalde (${reden.stdout || 'geen reden'}):\n${resultaat.uitvoer}`);
    }
    return JSON.parse(laatsteRegel) as T;
  }
}

function uniek(alias: string): string {
  return `${alias.toLowerCase()}-${RUN}-${++teller}`;
}

/** De eerste argumenten, zodat 120 transacties de foutmelding niet onleesbaar maken. */
function kort(args: string[]): string {
  return args.length <= 8 ? args.join(' ') : `${args.slice(0, 8).join(' ')} … (${args.length} argumenten)`;
}
