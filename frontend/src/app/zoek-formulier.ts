import { Component, input, output, signal } from '@angular/core';

/** S5.1: een zoekopdracht zoals de klant hem invult; lege velden ontbreken, bedragen blijven letterlijke tekst. */
export interface Zoekopdracht {
  tekst?: string;
  van?: string;
  tm?: string;
  type?: string;
  richting?: Richting;
}

type Richting = 'inkomend' | 'uitgaand';

/** De 7 typen uit het FO, in de volgorde van het FO. */
const TYPEN = [
  ['ONLINE_BANKIEREN', 'Online bankieren'],
  ['IDEAL_WERO', 'iDEAL | Wero'],
  ['BETAALAUTOMAAT', 'Betaalautomaat'],
  ['INCASSO', 'Incasso'],
  ['GELDAUTOMAAT', 'Geldautomaat'],
  ['OVERSCHRIJVING', 'Overschrijving'],
  ['VERZAMELBETALING', 'Verzamelbetaling'],
] as const;

/**
 * S5.1: zoeken in de transacties. "Zoeken in transacties" opent het formulier, "Verberg zoeken" sluit het. Het
 * tekstveld heet "Naam of IBAN": zoeken op bedrag of omschrijving in de tekst komt later (backlog). De controle van de
 * velden doet de API; een melding daarvan staat in het formulier.
 */
@Component({
  selector: 'app-zoek-formulier',
  template: `
    <button type="button" (click)="open.set(!open())">{{ open() ? 'Verberg zoeken' : 'Zoeken in transacties' }}</button>
    @if (open()) {
      <form aria-label="Zoeken in transacties" (submit)="$event.preventDefault(); zoeken()">
        <label for="zoek-tekst">Naam of IBAN</label>
        <input id="zoek-tekst" [value]="tekst()" (input)="tekst.set(waarde($event))" />
        <label for="zoek-van">Bedrag van (€)</label>
        <input id="zoek-van" inputmode="decimal" [value]="van()" (input)="van.set(waarde($event))" />
        <label for="zoek-tm">Bedrag t/m (€)</label>
        <input id="zoek-tm" inputmode="decimal" [value]="tm()" (input)="tm.set(waarde($event))" />
        <label for="zoek-type">Transactietype</label>
        <select id="zoek-type" (change)="type.set(waarde($event))">
          <option value="" [selected]="type() === ''">Alle transactietypes</option>
          @for (t of typen; track t[0]) {
            <option [value]="t[0]" [selected]="type() === t[0]">{{ t[1] }}</option>
          }
        </select>
        <fieldset>
          <legend>Filteren op</legend>
          <label
            ><input type="radio" name="richting" [checked]="richting() === undefined" (change)="richting.set(undefined)" />
            Alle transacties</label
          >
          <label
            ><input type="radio" name="richting" [checked]="richting() === 'uitgaand'" (change)="richting.set('uitgaand')" />
            Uitgaande transacties</label
          >
          <label
            ><input type="radio" name="richting" [checked]="richting() === 'inkomend'" (change)="richting.set('inkomend')" />
            Inkomende transacties</label
          >
        </fieldset>
        @if (melding(); as melding) {
          <p role="alert">{{ melding }}</p>
        }
        <button type="submit">Zoeken</button>
        <button type="button" (click)="wissen()">Wissen</button>
      </form>
    }
  `,
})
export class ZoekFormulier {
  /** Een melding van de API bij een ongeldige zoekopdracht, zoals een ongeldig bedragbereik. */
  readonly melding = input<string | undefined>(undefined);
  readonly zoek = output<Zoekopdracht>();
  readonly wis = output();

  protected readonly typen = TYPEN;
  protected readonly open = signal(false);
  protected readonly tekst = signal('');
  protected readonly van = signal('');
  protected readonly tm = signal('');
  protected readonly type = signal('');
  protected readonly richting = signal<Richting | undefined>(undefined);

  protected zoeken(): void {
    const opdracht: Zoekopdracht = {
      tekst: this.tekst().trim(),
      van: this.van().trim(),
      tm: this.tm().trim(),
      type: this.type(),
      richting: this.richting(),
    };
    this.zoek.emit(
      Object.fromEntries(Object.entries(opdracht).filter(([, waarde]) => waarde !== undefined && waarde !== '')),
    );
  }

  protected wissen(): void {
    this.tekst.set('');
    this.van.set('');
    this.tm.set('');
    this.type.set('');
    this.richting.set(undefined);
    this.wis.emit();
  }

  protected waarde(gebeurtenis: Event): string {
    return (gebeurtenis.target as HTMLInputElement | HTMLSelectElement).value;
  }
}
