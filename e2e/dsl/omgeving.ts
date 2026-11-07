import { createHash } from 'node:crypto';
import { expect } from '@playwright/test';
import type { BankDriver, RekeningInOverzicht, Spaarmutatie, TransactiesOpDatum } from '../drivers/bank-driver';
import type { K8sObject, Resultaat, ShellDriver } from '../drivers/shell/shell-driver';
import { DEMO_BEHEERDER, DEMO_KLANTEN, GELDAUTOMATEN, SUPERMARKTEN, VASTE_LASTEN, WEBWINKELS } from './huishoudens';
import { Stappen, type Stap } from './stappen';

const NAMESPACE = 'banksim-mse';
const RELEASE = 'banksim-mse';
const LABEL = `app.kubernetes.io/instance=${RELEASE}`;
const ORIGINEEL = 'banksim';
const DEMO_SECRET = 'bank-demo';
const GEMEENTE = 'Gemeente Zuidstad';
/** Energie heeft een eigen prijsreeks, niet de inflatie. */
const ENERGIE = 'Groenstroom Energie';

/** Per resource: uid, generation en een hash van spec of data. Verschilt er iets, dan is er iets veranderd. */
export type Momentopname = Record<string, string>;

/** Per demo-klant: zijn rekeningen met saldo en de transacties van zijn betaalrekening. */
export type DemoDataStand = Record<string, BetaalrekeningVanKlant>;

/** Een demo-klant met een werkgever, en zijn maandsalaris van 2021. */
export interface Werknemer {
  naam: string;
  werkgever: string;
  salaris: string;
}

/** Een demo-klant, van wie hij zijn inkomen krijgt, en het bedrag van 2021. */
export interface Inkomen {
  naam: string;
  van: string;
  bedrag: string;
}

/** Per jaar de factor ten opzichte van 2021, zoals "1.04". */
export type Loonstijging = Record<number, string>;

/** Per jaar het prijsniveau ten opzichte van 2021, zoals "1.10". */
export type Inflatie = Record<number, string>;

/** Een demo-klant die huurt, en aan wie hij de huur betaalt. */
export interface Huurder {
  naam: string;
  aan: string;
}

interface BetaalrekeningVanKlant {
  rekeningen: RekeningInOverzicht[];
  transacties: TransactiesOpDatum[];
}

/**
 * De omgeving zoals een beheerder hem ziet: installeren, verwijderen en controleren wat er in het cluster staat
 * (doc/werkwijze-mse.md, hoofdstuk 9.2). De beweringen staan hier; de driver voert alleen uit.
 */
export class Omgeving extends Stappen {
  private laatste?: Resultaat;
  /** De tag die deze omgeving het laatst installeerde; zonder tag geldt die van de bestaande installatie. */
  private geinstalleerd?: string;
  private beheerder?: BankDriver;

  constructor(
    private readonly shell: ShellDriver,
    private readonly app: BankDriver,
    /** De images die geïnstalleerd moeten worden; zonder tag hergebruikt installeer() de bestaande installatie. */
    private readonly imageTag: string | undefined,
    stap: Stap,
    /** Elke demo-gebruiker krijgt een eigen sessie: via de API, en via de browser voor wat alleen op het scherm bestaat. */
    private readonly maakRestDriver: () => Promise<BankDriver>,
    private readonly maakUiDriver: () => Promise<BankDriver>,
  ) {
    super(stap);
  }

  async installeer(): Promise<void> {
    const bron = this.imageTag ? ['--image-tag', this.imageTag] : ['--skip-build'];
    this.laatste = await this.shell.script('install.sh', ['--context', this.shell.context, ...bron]);
    verwachtGeslaagd('install.sh', this.laatste);
    this.geinstalleerd = this.imageTag;
  }

  /** Dezelfde images onder een nieuwe tag: Helm ziet een nieuwe versie en vervangt de pods van de applicatie. */
  async installeerNieuweVersie(): Promise<void> {
    const huidig = await this.geinstalleerdeTag();
    const nieuw = `${huidig}-upgrade`;
    await this.shell.tagImages(huidig, nieuw);
    this.laatste = await this.shell.script('install.sh', ['--context', this.shell.context, '--image-tag', nieuw]);
    verwachtGeslaagd('install.sh met een nieuwe versie', this.laatste);
    this.geinstalleerd = nieuw;
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

  /** S11.1: de wachtwoorden staan in het Secret dat install.sh aanmaakt, zoals de beheerder ze ook opzoekt. */
  async verwachtKlanten(aantal: number): Promise<void> {
    expect(DEMO_KLANTEN.length, 'klanten in de lijst van de DSL').toBe(aantal);
    const wachtwoord = await this.shell.secretWaarde(NAMESPACE, DEMO_SECRET, 'KLANT_WACHTWOORD');
    for (const { gebruikersnaam } of DEMO_KLANTEN) {
      const driver = await this.maakRestDriver();
      await driver.logIn(gebruikersnaam, wachtwoord);
      const soorten = (await driver.rekeningenInOverzicht()).map((rekening) => rekening.soort).sort();
      expect(soorten, `rekeningen in het overzicht van ${gebruikersnaam}`).toEqual(['betaal', 'spaar']);
    }
  }

  /** S11.1: boekingen na "vandaag" zijn onzichtbaar (ADR 0013); de fixture wist de datum weer (ADR 0014). */
  async zetSimulatiedatum(datum: string): Promise<void> {
    const driver = await this.demoBeheerder();
    expect(await driver.zetSimulatiedatumOpScherm(datum), 'simulatiedatum zetten als beheerder').toBe('gelukt');
  }

  async wisSimulatiedatum(): Promise<void> {
    const driver = await this.demoBeheerder();
    expect(await driver.wisSimulatiedatumOpScherm(), 'simulatiedatum wissen als beheerder').toBe('gelukt');
  }

  /** Elke klant bladert door zijn hele lijst, zoals met "Toon meer"; een ontbrekende maand noemt de bewering bij naam. */
  async verwachtTransactiesPerMaand(van: string, tm: string): Promise<void> {
    const verwacht = maanden(van, tm);
    for (const { gebruikersnaam } of DEMO_KLANTEN) {
      const { transacties } = await this.betaalrekeningVan(gebruikersnaam);
      const gevonden = new Set(transacties.map(({ datum }) => datum.slice(0, 7)));
      expect(verwacht.filter((maand) => !gevonden.has(maand)), `maanden zonder transacties bij ${gebruikersnaam}`).toEqual([]);
    }
  }

  /** S11.1: per klant en per maand het aantal betalingen aan een supermarkt, en elk bedrag in centen. */
  async verwachtBoodschappenPerMaand(van: string, tm: string, aantal: { min: number; max: number }, bedrag: { min: string; max: string }): Promise<void> {
    await this.verwachtAankopenPerMaand('boodschappen', SUPERMARKTEN, van, tm, aantal, bedrag);
  }

  /** S11.1: per klant en per maand een aantal betalingen aan webwinkels, elk binnen het bereik. */
  async verwachtWebwinkelsPerMaand(van: string, tm: string, aantal: { min: number; max: number }, bedrag: { min: string; max: string }): Promise<void> {
    await this.verwachtAankopenPerMaand('webwinkelaankopen', WEBWINKELS, van, tm, aantal, bedrag);
  }

  /** S11.1: per klant en per maand een aantal opnames bij een geldautomaat, elk binnen het bereik en in hele tientjes. */
  async verwachtGeldopnamesPerMaand(van: string, tm: string, aantal: { min: number; max: number }, bedrag: { min: string; max: string }): Promise<void> {
    await this.verwachtAankopenPerMaand('geldopnames', GELDAUTOMATEN, van, tm, aantal, bedrag);
    for (const { gebruikersnaam } of DEMO_KLANTEN) {
      const geenTientje = (await this.geldopnamesVan(gebruikersnaam)).filter((bedragVanOpname) => centen(bedragVanOpname) % 1000n !== 0n);
      expect(geenTientje, `geldopnames die geen hele tientjes zijn bij ${gebruikersnaam}`).toEqual([]);
    }
  }

  /** S11.1: "0 tot en met 2 per maand" klopt ook zonder opnames; daarom moet elke klant er minstens één hebben. */
  async verwachtMinstensEenGeldopname(): Promise<void> {
    for (const { gebruikersnaam } of DEMO_KLANTEN) {
      expect(await this.geldopnamesVan(gebruikersnaam), `geldopnames van ${gebruikersnaam}`).not.toEqual([]);
    }
  }

  private async geldopnamesVan(gebruikersnaam: string): Promise<string[]> {
    const { transacties } = await this.betaalrekeningVan(gebruikersnaam);
    return transacties.flatMap((dag) => dag.transacties.filter(({ tegenpartij }) => GELDAUTOMATEN.includes(tegenpartij)).map(({ bedrag }) => bedrag));
  }

  /** S11.1: per klant en per maand hoogstens een aantal betalingen aan andere klanten; elke betaling, ook een ontvangen, binnen het bereik. */
  async verwachtBetalingenAanAndereKlanten(van: string, tm: string, maximaal: number, bedrag: { min: string; max: string }): Promise<void> {
    for (const klant of DEMO_KLANTEN) {
      const betalingen = await this.betalingenTussenKlanten(klant);
      const perMaand = new Map(maanden(van, tm).map((maand) => [maand, 0]));
      for (const { datum } of betalingen.filter((betaling) => centen(betaling.bedrag) < 0n)) {
        const maand = datum.slice(0, 7);
        perMaand.set(maand, (perMaand.get(maand) ?? 0) + 1);
      }
      const fout = [...perMaand].filter(([, n]) => n > maximaal).map(([maand, n]) => `${maand}: ${n}`);
      expect(fout, `maanden met te veel betalingen aan andere klanten bij ${klant.gebruikersnaam}`).toEqual([]);
      const buitenBereik = betalingen
        .filter((betaling) => absoluut(centen(betaling.bedrag)) < centen(bedrag.min) || absoluut(centen(betaling.bedrag)) > centen(bedrag.max))
        .map((betaling) => `${betaling.datum}: ${betaling.bedrag}`);
      expect(buitenBereik, `betalingen buiten het bereik bij ${klant.gebruikersnaam}`).toEqual([]);
    }
  }

  /** S11.1: "hoogstens" klopt ook zonder betalingen; daarom moet elke klant er minstens één doen en één ontvangen. */
  async verwachtBetalingAanEnVanAndereKlant(): Promise<void> {
    for (const klant of DEMO_KLANTEN) {
      const betalingen = await this.betalingenTussenKlanten(klant);
      expect(betalingen.some((betaling) => centen(betaling.bedrag) < 0n), `betaling van ${klant.gebruikersnaam} aan een andere klant`).toBe(true);
      expect(betalingen.some((betaling) => centen(betaling.bedrag) > 0n), `betaling aan ${klant.gebruikersnaam} van een andere klant`).toBe(true);
    }
  }

  /** S11.1: elke afschrijving heeft een bijschrijving bij de ander, dus samen komen ze op nul uit. */
  async verwachtBetalingenTussenKlantenInBalans(): Promise<void> {
    let totaal = 0n;
    for (const klant of DEMO_KLANTEN) {
      for (const betaling of await this.betalingenTussenKlanten(klant)) totaal += centen(betaling.bedrag);
    }
    expect(totaal, 'som van de betalingen tussen de klanten, in centen').toBe(0n);
  }

  private async betalingenTussenKlanten(klant: { naam: string; gebruikersnaam: string }): Promise<{ datum: string; bedrag: string }[]> {
    const anderen = DEMO_KLANTEN.map(({ naam }) => naam).filter((naam) => naam !== klant.naam);
    const { transacties } = await this.betaalrekeningVan(klant.gebruikersnaam);
    return transacties.flatMap((dag) =>
      dag.transacties.filter(({ tegenpartij }) => anderen.includes(tegenpartij)).map(({ bedrag }) => ({ datum: dag.datum, bedrag })),
    );
  }

  private async verwachtAankopenPerMaand(
    soort: string,
    tegenpartijen: string[],
    van: string,
    tm: string,
    aantal: { min: number; max: number },
    bedrag: { min: string; max: string },
  ): Promise<void> {
    for (const { gebruikersnaam } of DEMO_KLANTEN) {
      const { transacties } = await this.betaalrekeningVan(gebruikersnaam);
      const perMaand = new Map(maanden(van, tm).map((maand) => [maand, 0]));
      const buitenBereik: string[] = [];
      for (const dag of transacties.filter(({ datum }) => perMaand.has(datum.slice(0, 7)))) {
        for (const transactie of dag.transacties.filter(({ tegenpartij }) => tegenpartijen.includes(tegenpartij))) {
          const maand = dag.datum.slice(0, 7);
          perMaand.set(maand, (perMaand.get(maand) ?? 0) + 1);
          const afgeschreven = -centen(transactie.bedrag);
          if (afgeschreven < centen(bedrag.min) || afgeschreven > centen(bedrag.max)) buitenBereik.push(`${dag.datum}: ${transactie.bedrag}`);
        }
      }
      const fout = [...perMaand].filter(([, n]) => n < aantal.min || n > aantal.max).map(([maand, n]) => `${maand}: ${n}`);
      expect(fout, `maanden met een verkeerd aantal ${soort} bij ${gebruikersnaam}`).toEqual([]);
      expect(buitenBereik, `${soort} buiten het bereik bij ${gebruikersnaam}`).toEqual([]);
    }
  }

  /** S11.1: per klant en per maand precies één betaling aan elk van de vaste lasten. */
  async verwachtVasteLastenPerMaand(van: string, tm: string): Promise<void> {
    for (const { gebruikersnaam } of DEMO_KLANTEN) {
      const { transacties } = await this.betaalrekeningVan(gebruikersnaam);
      const fout = maanden(van, tm).flatMap((maand) =>
        VASTE_LASTEN.map((last) => ({ maand, last, aantal: betalingenAan(transacties, maand, last).length }))
          .filter(({ aantal }) => aantal !== 1)
          .map(({ maand: m, last, aantal }) => `${m} ${last}: ${aantal}`),
      );
      expect(fout, `vaste lasten die niet precies één keer zijn betaald door ${gebruikersnaam}`).toEqual([]);
    }
  }

  /** S11.1: de bedragen in domeinnotatie die de klant in die maand aan elk van de vaste lasten betaalde. */
  async verwachtVasteLastenVan(naam: string, maand: string, verwacht: Record<string, string>): Promise<void> {
    const { transacties } = await this.betaalrekeningVan(gebruikersnaamVan(naam));
    const betaald = Object.fromEntries(VASTE_LASTEN.map((last) => [last, betalingenAan(transacties, maand, last)[0]]));
    expect(betaald, `vaste lasten van ${naam} in ${maand}`).toEqual(verwacht);
  }

  /** S11.1: in mei van elk jaar komen van de werkgever precies twee bedragen binnen: het salaris van dat jaar en 92% daarvan. */
  async verwachtVakantiegeldInMei(werknemers: Werknemer[], loonstijging: Loonstijging, jaren: number[]): Promise<void> {
    for (const { naam, werkgever, salaris } of werknemers) {
      const { transacties } = await this.betaalrekeningVan(gebruikersnaamVan(naam));
      for (const jaar of jaren) {
        const salarisVanDatJaar = maal(salaris, loonstijging[jaar]);
        const ontvangen = betalingenAan(transacties, `${jaar}-05`, werkgever).sort();
        expect(ontvangen, `van ${werkgever} aan ${naam} in mei ${jaar}`).toEqual([salarisVanDatJaar, maal(salarisVanDatJaar, '0.92')].sort());
      }
    }
  }

  /** S11.1: de bedragen in domeinnotatie die de klant in die maand van die tegenpartij kreeg, in willekeurige volgorde. */
  async verwachtBetalingenVan(naam: string, van: string, maand: string, bedragen: string[]): Promise<void> {
    const { transacties } = await this.betaalrekeningVan(gebruikersnaamVan(naam));
    expect(betalingenAan(transacties, maand, van).sort(), `van ${van} aan ${naam} in ${maand}`).toEqual([...bedragen].sort());
  }

  /** S11.1: buiten mei komt van de werkgever elke maand precies één bedrag binnen: het salaris van dat jaar. */
  async verwachtAlleenSalarisBuitenMei(werknemers: Werknemer[], loonstijging: Loonstijging, van: string, tm: string): Promise<void> {
    for (const { naam, werkgever, salaris } of werknemers) {
      const { transacties } = await this.betaalrekeningVan(gebruikersnaamVan(naam));
      const fout = maanden(van, tm)
        .filter((maand) => !maand.endsWith('-05'))
        .map((maand) => ({ maand, ontvangen: betalingenAan(transacties, maand, werkgever) }))
        .filter(({ maand, ontvangen }) => ontvangen.length !== 1 || ontvangen[0] !== maal(salaris, loonstijging[jaarVan(maand)]))
        .map(({ maand, ontvangen }) => `${maand}: ${ontvangen.join(', ')}`);
      expect(fout, `maanden waarin ${naam} iets anders krijgt dan het salaris van ${werkgever}`).toEqual([]);
    }
  }

  /** S11.1: elk jaar precies één bijschrijving van de Belastingdienst, in april, mei of juni en binnen het bereik; ook geen in een andere maand. */
  async verwachtBelastingteruggave(behalve: string, jaren: number[], bedrag: { min: string; max: string }): Promise<void> {
    for (const { naam, gebruikersnaam } of DEMO_KLANTEN.filter((klant) => klant.naam !== behalve)) {
      const teruggaven = await this.bedragenVanDeBelastingdienst(gebruikersnaam);
      const fout = jaren
        .map((jaar) => ({ jaar, aantal: teruggaven.filter(({ datum }) => datum.startsWith(`${jaar}-`)).length }))
        .filter(({ aantal }) => aantal !== 1)
        .map(({ jaar, aantal }) => `${jaar}: ${aantal}`);
      expect(fout, `jaren zonder precies één teruggave bij ${naam}`).toEqual([]);
      const buitenBereik = teruggaven
        .filter((teruggave) => !['04', '05', '06'].includes(teruggave.datum.slice(5, 7)) || centen(teruggave.bedrag) < centen(bedrag.min) || centen(teruggave.bedrag) > centen(bedrag.max))
        .map((teruggave) => `${teruggave.datum}: ${teruggave.bedrag}`);
      expect(buitenBereik, `teruggaven buiten de maanden of het bereik bij ${naam}`).toEqual([]);
      expect(teruggaven, `teruggaven buiten de jaren bij ${naam}`).toHaveLength(jaren.length);
    }
  }

  /** S11.1: een zzp'er krijgt niets. */
  async verwachtNietsVanDeBelastingdienst(naam: string): Promise<void> {
    expect(await this.bedragenVanDeBelastingdienst(gebruikersnaamVan(naam)), `van de Belastingdienst aan ${naam}`).toEqual([]);
  }

  private async bedragenVanDeBelastingdienst(gebruikersnaam: string): Promise<{ datum: string; bedrag: string }[]> {
    const { transacties } = await this.betaalrekeningVan(gebruikersnaam);
    return transacties.flatMap((dag) =>
      dag.transacties.filter(({ tegenpartij }) => tegenpartij === 'Belastingdienst').map(({ bedrag }) => ({ datum: dag.datum, bedrag })),
    );
  }

  /** S11.1: per klant precies één termijn in elke maand van maart t/m december. */
  async verwachtGemeentelijkeBelastingenPerMaand(van: string, tm: string): Promise<void> {
    for (const { gebruikersnaam } of DEMO_KLANTEN) {
      const { transacties } = await this.betaalrekeningVan(gebruikersnaam);
      const fout = maanden(van, tm)
        .filter((maand) => !isJanuariOfFebruari(maand))
        .map((maand) => ({ maand, aantal: betalingenAan(transacties, maand, GEMEENTE).length }))
        .filter(({ aantal }) => aantal !== 1)
        .map(({ maand, aantal }) => `${maand}: ${aantal}`);
      expect(fout, `maanden zonder precies één termijn bij ${gebruikersnaam}`).toEqual([]);
    }
  }

  /** S11.1: de termijnen beginnen in maart. */
  async verwachtGeenGemeentelijkeBelastingenInJanuariEnFebruari(van: string, tm: string): Promise<void> {
    for (const { gebruikersnaam } of DEMO_KLANTEN) {
      const { transacties } = await this.betaalrekeningVan(gebruikersnaam);
      const betaald = maanden(van, tm).filter(isJanuariOfFebruari).flatMap((maand) => betalingenAan(transacties, maand, GEMEENTE));
      expect(betaald, `termijnen in januari of februari bij ${gebruikersnaam}`).toEqual([]);
    }
  }

  /** S11.1: elke termijn van deze klanten is het bedrag van 2021 maal de inflatie van het jaar van die termijn. */
  async verwachtTermijnGemeentelijkeBelastingen(namen: string[], bedrag: string, inflatie: Inflatie): Promise<void> {
    for (const naam of namen) {
      const { transacties } = await this.betaalrekeningVan(gebruikersnaamVan(naam));
      const fout = transacties.flatMap(({ datum, transacties: opDatum }) => opDatum
        .filter(({ tegenpartij }) => tegenpartij === GEMEENTE)
        .filter((t) => t.bedrag !== afschrijving(maal(bedrag, inflatie[jaarVan(datum)])))
        .map((t) => `${datum}: ${t.bedrag}`));
      expect(fout, `termijnen van ${naam} die niet € ${bedrag} maal de inflatie zijn`).toEqual([]);
    }
  }

  async verwachtTermijnGemeentelijkeBelastingenVanDeAnderen(namen: string[], bedrag: string, inflatie: Inflatie): Promise<void> {
    const anderen = DEMO_KLANTEN.map(({ naam }) => naam).filter((naam) => !namen.includes(naam));
    expect(anderen, 'de andere klanten').toHaveLength(6);
    await this.verwachtTermijnGemeentelijkeBelastingen(anderen, bedrag, inflatie);
  }

  /**
   * S11.1: elke maand betaalt elke klant zijn huur en zijn vaste lasten behalve de energie precies één keer, en wel het bedrag
   * van de eerste maand maal de inflatie van dat jaar.
   */
  async verwachtVasteLastenMetInflatie(huurders: Huurder[], inflatie: Inflatie, van: string, tm: string): Promise<void> {
    huurders.forEach(({ naam }) => gebruikersnaamVan(naam)); // een tikfout in een naam valt zo op, in plaats van dat zijn huur niet wordt gecontroleerd
    for (const { naam, gebruikersnaam } of DEMO_KLANTEN) {
      const huur = huurders.filter((huurder) => huurder.naam === naam).map(({ aan }) => aan);
      const { transacties } = await this.betaalrekeningVan(gebruikersnaam);
      for (const aan of [...huur, ...VASTE_LASTEN.filter((last) => last !== ENERGIE)]) {
        const eerste = betalingenAan(transacties, van, aan);
        expect(eerste, `${naam} aan ${aan} in ${van}`).toHaveLength(1);
        const fout = maanden(van, tm)
          .map((maand) => ({ maand, betaald: betalingenAan(transacties, maand, aan) }))
          .filter(({ maand, betaald }) => betaald.join() !== afschrijving(maal(bedragVan(eerste[0]), inflatie[jaarVan(maand)])))
          .map(({ maand, betaald }) => `${maand}: ${betaald.join(', ')}`);
        expect(fout, `maanden waarin ${naam} aan ${aan} niet € ${bedragVan(eerste[0])} maal de inflatie betaalt`).toEqual([]);
      }
    }
  }

  /** S11.1: van elke klant is elke termijn het bedrag van de eerste termijn maal de inflatie van het jaar van die termijn. */
  async verwachtGemeentelijkeBelastingenMetInflatie(inflatie: Inflatie, van: string): Promise<void> {
    for (const { naam, gebruikersnaam } of DEMO_KLANTEN) {
      const { transacties } = await this.betaalrekeningVan(gebruikersnaam);
      const eerste = betalingenAan(transacties, van, GEMEENTE);
      expect(eerste, `${naam} aan ${GEMEENTE} in ${van}`).toHaveLength(1);
      await this.verwachtTermijnGemeentelijkeBelastingen([naam], bedragVan(eerste[0]), inflatie);
    }
  }

  /** S11.1: in elke maand betalen deze klanten precies één keer aan de tegenpartij, en elke keer hetzelfde bedrag. */
  async verwachtElkeMaandHetzelfdeBedragAan(namen: string[], aan: string, van: string, tm: string): Promise<void> {
    for (const naam of namen) {
      const { transacties } = await this.betaalrekeningVan(gebruikersnaamVan(naam));
      const perMaand = maanden(van, tm).map((maand) => betalingenAan(transacties, maand, aan));
      expect(perMaand.filter((betaald) => betaald.length !== 1), `maanden waarin ${naam} niet precies één keer aan ${aan} betaalt`).toEqual([]);
      expect(new Set(perMaand.flat()).size, `verschillende bedragen van ${naam} aan ${aan}`).toBe(1);
    }
  }

  /** S11.1: in elke maand betaalt de klant precies één keer het bedrag van dat jaar aan de verhuurder, in domeinnotatie. */
  async verwachtWoonlastenPerJaar(naam: string, aan: string, perJaar: Record<number, string>, van: string, tm: string): Promise<void> {
    await this.verwachtElkeMaandEenKeer(naam, aan, perJaar, van, tm, `maanden waarin ${naam} niet precies één keer de huur van dat jaar aan ${aan} betaalt`);
  }

  /** S11.1: per klant en per maand precies één inleg op de spaarrekening op de 25e; de extra inleg in mei valt op de 27e. */
  async verwachtInlegPerMaand(van: string, tm: string): Promise<void> {
    for (const { gebruikersnaam } of DEMO_KLANTEN) {
      const mutaties = await this.spaarmutatiesVan(gebruikersnaam);
      const fout = maanden(van, tm)
        .map((maand) => ({ maand, aantal: mutaties.filter((m) => m.type === 'Inleg' && m.datum === `${maand}-25`).length }))
        .filter(({ aantal }) => aantal !== 1)
        .map(({ maand, aantal }) => `${maand}: ${aantal}`);
      expect(fout, `maanden zonder precies één inleg op de 25e bij ${gebruikersnaam}`).toEqual([]);
    }
  }

  /** S11.1: naast de vaste inleg op de 25e alleen de extra inleg op 27 mei van elk jaar: 40% van het salaris van dat jaar. */
  async verwachtExtraInlegInMei(werknemers: Werknemer[], loonstijging: Loonstijging, jaren: number[]): Promise<void> {
    for (const { naam, salaris } of werknemers) {
      const extra = inleggenBuitenDe25e(await this.spaarmutatiesVan(gebruikersnaamVan(naam)));
      const verwacht = jaren.map((jaar) => ({ datum: `${jaar}-05-27`, bedrag: maal(maal(salaris, loonstijging[jaar]), '0.40') }));
      expect(extra, `inleggen van ${naam} buiten de 25e`).toEqual(verwacht);
    }
  }

  /** S11.1: deze inleggen buiten de 25e staan op de spaarrekening, met datum en bedrag in domeinnotatie. */
  async verwachtExtraInleg(naam: string, verwacht: { datum: string; bedrag: string }[]): Promise<void> {
    const extra = inleggenBuitenDe25e(await this.spaarmutatiesVan(gebruikersnaamVan(naam)));
    expect(extra, `inleggen van ${naam} buiten de 25e`).toEqual(expect.arrayContaining(verwacht));
  }

  /** S11.1: elke maand komt van de bron precies één keer het inkomen van 2021 maal de loonstijging van dat jaar binnen. */
  async verwachtInkomenMetLoonstijging(inkomens: Inkomen[], loonstijging: Loonstijging, van: string, tm: string): Promise<void> {
    for (const { naam, van: bron, bedrag } of inkomens) {
      const perJaar = Object.fromEntries(Object.entries(loonstijging).map(([jaar, factor]) => [jaar, maal(bedrag, factor)]));
      await this.verwachtInkomenPerJaar(naam, bron, perJaar, van, tm);
    }
  }

  /** S11.1: in elke maand komt van de bron precies één keer het bedrag van dat jaar binnen, in domeinnotatie. */
  async verwachtInkomenPerJaar(naam: string, bron: string, perJaar: Record<number, string>, van: string, tm: string): Promise<void> {
    await this.verwachtElkeMaandEenKeer(naam, bron, perJaar, van, tm, `maanden waarin ${naam} niet precies één keer zijn inkomen van ${bron} krijgt`);
  }

  /** Per maand precies één boeking met de tegenpartij met het bedrag van dat jaar; andere bedragen met dezelfde tegenpartij mogen ook. */
  private async verwachtElkeMaandEenKeer(naam: string, tegenpartij: string, perJaar: Record<number, string>, van: string, tm: string, melding: string): Promise<void> {
    const { transacties } = await this.betaalrekeningVan(gebruikersnaamVan(naam));
    const fout = maanden(van, tm)
      .map((maand) => ({ maand, geboekt: betalingenAan(transacties, maand, tegenpartij) }))
      .filter(({ maand, geboekt }) => geboekt.filter((bedrag) => bedrag === perJaar[jaarVan(maand)]).length !== 1)
      .map(({ maand, geboekt }) => `${maand}: ${geboekt.join(', ')}`);
    expect(fout, melding).toEqual([]);
  }

  async verwachtAlleenInlegOpDe25e(namen: string[]): Promise<void> {
    for (const naam of namen) {
      const extra = inleggenBuitenDe25e(await this.spaarmutatiesVan(gebruikersnaamVan(naam)));
      expect(extra, `inleggen van ${naam} buiten de 25e`).toEqual([]);
    }
  }

  /** S11.1: de spaarrekening heeft de naam van de klant, dus de afschrijving staat onder zijn eigen naam. */
  async verwachtAfschrijvingNaarSpaarrekening(naam: string, maand: string, bedrag: string): Promise<void> {
    const { transacties } = await this.betaalrekeningVan(gebruikersnaamVan(naam));
    expect(betalingenAan(transacties, maand, naam), `naar de spaarrekening van ${naam} in ${maand}`).toEqual([bedrag]);
  }

  async verwachtSpaarsaldo(naam: string, saldo: string): Promise<void> {
    const { rekeningen } = await this.betaalrekeningVan(gebruikersnaamVan(naam));
    expect(rekeningen.find((rekening) => rekening.soort === 'spaar')?.saldo, `spaarsaldo van ${naam}`).toBe(saldo);
  }

  /** S11.1: de opnames op de spaarrekening van de klant, op datum, met het bedrag in domeinnotatie. */
  async verwachtOpnames(naam: string, verwacht: { datum: string; bedrag: string }[]): Promise<void> {
    const mutaties = await this.spaarmutatiesVan(gebruikersnaamVan(naam));
    const opnames = mutaties
      .filter((mutatie) => mutatie.type === 'Opname')
      .map(({ datum, bedrag }) => ({ datum, bedrag }))
      .sort((a, b) => a.datum.localeCompare(b.datum));
    expect(opnames, `opnames van ${naam}`).toEqual(verwacht);
  }

  async verwachtGeenOpnamesBehalveBij(namen: string[]): Promise<void> {
    for (const { naam, gebruikersnaam } of DEMO_KLANTEN.filter((klant) => !namen.includes(klant.naam))) {
      const mutaties = await this.spaarmutatiesVan(gebruikersnaam);
      expect(mutaties.filter((mutatie) => mutatie.type === 'Opname'), `opnames van ${naam}`).toEqual([]);
    }
  }

  /** S11.1: de rente op één datum, met het bedrag in domeinnotatie. */
  async verwachtRente(naam: string, datum: string, bedrag: string): Promise<void> {
    const mutaties = await this.spaarmutatiesVan(gebruikersnaamVan(naam));
    const rente = mutaties.filter((mutatie) => mutatie.type === 'Rente' && mutatie.datum === datum).map((mutatie) => mutatie.bedrag);
    expect(rente, `rente van ${naam} op ${datum}`).toEqual([bedrag]);
  }

  /** S11.1: per klant en per maand precies één rentebijschrijving, op de laatste dag van de maand. */
  async verwachtRentePerMaand(van: string, tm: string): Promise<void> {
    for (const { gebruikersnaam } of DEMO_KLANTEN) {
      const rente = (await this.spaarmutatiesVan(gebruikersnaam)).filter((mutatie) => mutatie.type === 'Rente');
      const datums = rente.map((mutatie) => mutatie.datum).sort();
      expect(datums, `rentedatums van ${gebruikersnaam}`).toEqual(maanden(van, tm).map(laatsteDag));
    }
  }

  /** Inloggen als de demo-klant, en alle mutaties van zijn spaarrekening laden. */
  private async spaarmutatiesVan(gebruikersnaam: string): Promise<Spaarmutatie[]> {
    const driver = await this.maakRestDriver();
    await driver.logIn(gebruikersnaam, await this.shell.secretWaarde(NAMESPACE, DEMO_SECRET, 'KLANT_WACHTWOORD'));
    const spaar = (await driver.rekeningenInOverzicht()).find((rekening) => rekening.soort === 'spaar');
    if (!spaar) throw new Error(`${gebruikersnaam} heeft geen spaarrekening`);
    await driver.openSpaarrekening(spaar.iban);
    while (await driver.heeftMeer()) await driver.toonMeer();
    return driver.spaarmutaties();
  }

  /** S11.1: vanaf het eindsaldo terugrekenen, dag voor dag en in centen: elk saldo aan het eind van een dag, en het beginsaldo. */
  async verwachtNooitRood(): Promise<void> {
    for (const { gebruikersnaam } of DEMO_KLANTEN) {
      const { rekeningen, transacties } = await this.betaalrekeningVan(gebruikersnaam);
      let lopend = centen(betaalrekening(rekeningen, gebruikersnaam).saldo);
      let laagste = lopend;
      for (const dag of transacties) {
        for (const { bedrag } of dag.transacties) lopend -= centen(bedrag);
        if (lopend < laagste) laagste = lopend;
      }
      expect(laagste >= 0n, `laagste lopende saldo van ${gebruikersnaam}: ${laagste} cent`).toBe(true);
    }
  }

  /** S11.1: wat de klanten nu zien, om na een upgrade te vergelijken. */
  async demoData(): Promise<DemoDataStand> {
    const stand: DemoDataStand = {};
    for (const { gebruikersnaam } of DEMO_KLANTEN) {
      stand[gebruikersnaam] = await this.betaalrekeningVan(gebruikersnaam);
    }
    return stand;
  }

  async verwachtDemoDataGelijkAan(voor: DemoDataStand): Promise<void> {
    expect(await this.demoData(), 'rekeningen en transacties van de demo-klanten').toEqual(voor);
  }

  /** Inloggen als de demo-klant, en de hele transactielijst van zijn betaalrekening laden. */
  private async betaalrekeningVan(gebruikersnaam: string): Promise<BetaalrekeningVanKlant> {
    const driver = await this.maakRestDriver();
    await driver.logIn(gebruikersnaam, await this.shell.secretWaarde(NAMESPACE, DEMO_SECRET, 'KLANT_WACHTWOORD'));
    const rekeningen = await driver.rekeningenInOverzicht();
    await driver.openRekening(betaalrekening(rekeningen, gebruikersnaam).iban);
    while (await driver.heeftMeer()) await driver.toonMeer();
    return { rekeningen, transacties: await driver.transacties() };
  }

  private async demoBeheerder(): Promise<BankDriver> {
    if (!this.beheerder) {
      this.beheerder = await this.maakRestDriver();
      await this.beheerder.logIn(DEMO_BEHEERDER, await this.shell.secretWaarde(NAMESPACE, DEMO_SECRET, 'BEHEERDER_WACHTWOORD'));
    }
    return this.beheerder;
  }

  async verwachtBeheerder(): Promise<void> {
    const driver = await this.maakUiDriver();
    await driver.logIn(DEMO_BEHEERDER, await this.shell.secretWaarde(NAMESPACE, DEMO_SECRET, 'BEHEERDER_WACHTWOORD'));
    expect(await driver.scherm(), 'scherm na het inloggen').toBe('beheer');
  }

  async verwachtBereikbaar(): Promise<void> {
    await this.app.openStartpagina();
    expect(await this.app.versie(), 'versie van de installatie').toBe(await this.geinstalleerdeTag());
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

  private async geinstalleerdeTag(): Promise<string> {
    const tag = this.geinstalleerd ?? (await this.shell.helmWaarden(NAMESPACE, RELEASE))?.imageTag;
    if (typeof tag !== 'string') throw new Error(`BankSim-MSE is niet geïnstalleerd in ${this.shell.context}`);
    return tag;
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

function gebruikersnaamVan(naam: string): string {
  const klant = DEMO_KLANTEN.find((demo) => demo.naam === naam);
  if (!klant) throw new Error(`${naam} is geen demo-klant`);
  return klant.gebruikersnaam;
}

/** Op datum oplopend, zodat de volgorde van het scherm er niet toe doet. */
function inleggenBuitenDe25e(mutaties: Spaarmutatie[]): { datum: string; bedrag: string }[] {
  return mutaties
    .filter((mutatie) => mutatie.type === 'Inleg' && !mutatie.datum.endsWith('-25'))
    .map(({ datum, bedrag }) => ({ datum, bedrag }))
    .sort((a, b) => a.datum.localeCompare(b.datum));
}

function isJanuariOfFebruari(maand: string): boolean {
  return maand.endsWith('-01') || maand.endsWith('-02');
}

/** De bedragen van de betalingen aan een tegenpartij in een maand, zoals "2026-12". */
function betalingenAan(transacties: TransactiesOpDatum[], maand: string, tegenpartij: string): string[] {
  return transacties
    .filter(({ datum }) => datum.startsWith(maand))
    .flatMap((dag) => dag.transacties.filter((t) => t.tegenpartij === tegenpartij).map((t) => t.bedrag));
}

function betaalrekening(rekeningen: RekeningInOverzicht[], gebruikersnaam: string): RekeningInOverzicht {
  const betaal = rekeningen.find((rekening) => rekening.soort === 'betaal');
  if (!betaal) throw new Error(`${gebruikersnaam} heeft geen betaalrekening`);
  return betaal;
}

function absoluut(centenBedrag: bigint): bigint {
  return centenBedrag < 0n ? -centenBedrag : centenBedrag;
}

/** Een bedrag in domeinnotatie ("-950.00") in centen; geen drijvende komma (ADR 0010). */
function centen(bedrag: string): bigint {
  return BigInt(bedrag.replace('.', ''));
}

/** Een positief bedrag maal een factor met twee decimalen ("1.04"), afgerond op centen, de helft naar boven. */
function maal(bedrag: string, factor: string): string {
  const uitkomst = (centen(bedrag) * centen(factor) + 50n) / 100n;
  return `${uitkomst / 100n}.${String(uitkomst % 100n).padStart(2, '0')}`;
}

/** Een afschrijving in domeinnotatie: "38.00" wordt "-38.00". */
function afschrijving(bedrag: string): string {
  return `-${bedrag}`;
}

/** Het bedrag zonder teken: "-38.00" wordt "38.00". */
function bedragVan(afschrijving: string): string {
  return afschrijving.replace(/^-/, '');
}

/** Het jaar van een maand zoals "2021-10", of van een datum zoals "2021-10-28". */
function jaarVan(maand: string): number {
  return Number(maand.slice(0, 4));
}

/** De laatste dag van een maand: "2021-10" wordt "2021-10-31". */
function laatsteDag(maand: string): string {
  const [jaar, nummer] = maand.split('-').map(Number);
  return new Date(Date.UTC(jaar, nummer, 0)).toISOString().slice(0, 10);
}

/** De maanden van `van` t/m `tm`, zoals "2021-10". */
function maanden(van: string, tm: string): string[] {
  const resultaat: string[] = [];
  let [jaar, maand] = van.split('-').map(Number);
  for (let huidig = van; huidig <= tm; huidig = `${jaar}-${String(maand).padStart(2, '0')}`) {
    resultaat.push(huidig);
    maand += 1;
    if (maand > 12) {
      maand = 1;
      jaar += 1;
    }
  }
  return resultaat;
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
