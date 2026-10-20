import { createHash } from 'node:crypto';
import { expect } from '@playwright/test';
import type { BankDriver } from '../drivers/bank-driver';
import type { K8sObject, Resultaat, ShellDriver } from '../drivers/shell/shell-driver';
import { Stappen, type Stap } from './stappen';

const NAMESPACE = 'banksim-mse';
const RELEASE = 'banksim-mse';
const LABEL = `app.kubernetes.io/instance=${RELEASE}`;
const ORIGINEEL = 'banksim';

/** Per resource: uid, generation en een hash van spec of data. Verschilt er iets, dan is er iets veranderd. */
export type Momentopname = Record<string, string>;

/**
 * De omgeving zoals een beheerder hem ziet: installeren, verwijderen en controleren wat er in het cluster staat
 * (doc/werkwijze-mse.md, hoofdstuk 9.2). De beweringen staan hier; de driver voert alleen uit.
 */
export class Omgeving extends Stappen {
  private laatste?: Resultaat;

  constructor(
    private readonly shell: ShellDriver,
    private readonly app: BankDriver,
    /** De images die geïnstalleerd moeten worden; zonder tag hergebruikt installeer() de bestaande installatie. */
    private readonly imageTag: string | undefined,
    stap: Stap,
  ) {
    super(stap);
  }

  async installeer(): Promise<void> {
    const bron = this.imageTag ? ['--image-tag', this.imageTag] : ['--skip-build'];
    this.laatste = await this.shell.script('install.sh', ['--context', this.shell.context, ...bron]);
    verwachtGeslaagd('install.sh', this.laatste);
  }

  async installeerMetActieveContext(actief: string): Promise<void> {
    this.laatste = await this.shell.metActieveContext(actief, (kubeconfig) =>
      this.shell.script('install.sh', ['--skip-build'], { KUBECONFIG: kubeconfig }),
    );
    verwachtGeslaagd('install.sh zonder --context', this.laatste);
  }

  async installeerMetContext(context: string): Promise<void> {
    this.laatste = await this.shell.script('install.sh', ['--context', context, '--skip-build']);
  }

  async verwijder(): Promise<void> {
    this.laatste = await this.shell.script('uninstall.sh', ['--context', this.shell.context, '-y']);
    verwachtGeslaagd('uninstall.sh', this.laatste);
  }

  revisie(): Promise<number> {
    return this.shell.helmRevisie(NAMESPACE, RELEASE);
  }

  /**
   * De resources van de release, hun pods en de Secrets die install.sh aanmaakt (ADR 0009); een herstart geeft
   * nieuwe pod-uid's, een nieuw wachtwoord een andere inhoud.
   */
  async momentopname(): Promise<Momentopname> {
    return maakMomentopname(
      await this.shell.objecten(['deployment,statefulset,service,ingress,configmap,secret,pod', '-n', NAMESPACE, '-l', LABEL]),
    );
  }

  async verwachtOngewijzigd(voor: Momentopname): Promise<void> {
    expect(await this.momentopname(), 'resources en pods van BankSim-MSE').toEqual(voor);
  }

  async verwachtBereikbaar(): Promise<void> {
    await this.app.openStartpagina();
    const verwacht = this.imageTag ?? (await this.shell.helmWaarden(NAMESPACE, RELEASE))?.imageTag;
    expect(await this.app.versie(), 'versie van de installatie').toBe(verwacht);
  }

  async verwachtAllesInNamespace(): Promise<void> {
    const objecten = await this.shell.objecten(['all,ingress,configmap,secret,serviceaccount', '-A', '-l', LABEL]);
    expect(objecten.length, `resources met label ${LABEL}`).toBeGreaterThan(0);
    expect(objecten.filter((o) => o.metadata.namespace !== NAMESPACE).map(naam), 'buiten banksim-mse').toEqual([]);
    expect((await this.clusterBreed()).map(naam), 'cluster-brede resources').toEqual([]);
  }

  async verwachtGeinstalleerdNa(revisie: number): Promise<void> {
    expect(this.laatste?.uitvoer).toContain('Context kind-single-node');
    expect(await this.revisie(), 'Helm-revisie in deze omgeving').toBeGreaterThan(revisie);
  }

  async verwachtGeweigerd(context: string): Promise<void> {
    expect(this.laatste?.code, 'exitcode').not.toBe(0);
    expect(this.laatste?.uitvoer).toContain(`kubectl-context '${context}' bestaat niet`);
  }

  async verwachtGeenNamespace(): Promise<void> {
    expect(await this.shell.bestaat('namespace', NAMESPACE), `namespace ${NAMESPACE} bestaat`).toBe(false);
  }

  async verwachtGeenResourcesVanDeRelease(): Promise<void> {
    const objecten = await this.shell.objecten(['all,ingress,configmap,secret,serviceaccount', '-A', '-l', LABEL]);
    expect([...objecten, ...(await this.clusterBreed())].map(naam), `resources met label ${LABEL}`).toEqual([]);
  }

  origineelAanwezig(): Promise<boolean> {
    return this.shell.bestaat('namespace', ORIGINEEL);
  }

  origineel(): Origineel {
    return new Origineel(this.shell);
  }

  private clusterBreed(): Promise<K8sObject[]> {
    return this.shell.objecten(['clusterrole,clusterrolebinding,ingressclass,persistentvolume', '-l', LABEL]);
  }
}

/** Het origineel in namespace banksim: mag door BankSim-MSE nooit geraakt worden (ADR 0004). */
export class Origineel {
  constructor(private readonly shell: ShellDriver) {}

  async momentopname(): Promise<Momentopname> {
    return maakMomentopname(
      await this.shell.objecten([
        'deployment,statefulset,service,ingress,configmap,secret,persistentvolumeclaim,pod',
        '-n',
        ORIGINEEL,
      ]),
    );
  }

  async verwachtGelijkAan(voor: Momentopname): Promise<void> {
    expect(await this.momentopname(), `resources in namespace ${ORIGINEEL}`).toEqual(voor);
  }

  async verwachtBereikbaar(): Promise<void> {
    expect(await this.shell.httpStatus('https://bank.localtest.me/'), 'https://bank.localtest.me/').toBe(200);
  }
}

function maakMomentopname(objecten: K8sObject[]): Momentopname {
  return Object.fromEntries(
    objecten.map((o) => {
      const inhoud = createHash('sha256')
        .update(JSON.stringify(o.spec ?? o.data ?? null))
        .digest('hex')
        .slice(0, 12);
      return [naam(o), `${o.metadata.uid} gen=${o.metadata.generation ?? '-'} ${inhoud}`];
    }),
  );
}

function naam(o: K8sObject): string {
  return `${o.kind}/${o.metadata.namespace ? `${o.metadata.namespace}/` : ''}${o.metadata.name}`;
}

function verwachtGeslaagd(wat: string, resultaat: Resultaat): void {
  expect(resultaat.code, `${wat} faalde:\n${resultaat.uitvoer}`).toBe(0);
}
