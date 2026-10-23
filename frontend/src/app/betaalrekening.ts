import { Component, computed, input, output, signal } from '@angular/core';
import { datumregel } from './datum/datumregel';
import { datumZonderDag, tijdstipVoorWeergave } from './datum/tijdstip';
import { bedragMetTeken } from './geld/bedrag';
import { typeVoorWeergave } from './transactie/transactietype';

/** Zoals bank-api hem levert, van nieuw naar oud; het bedrag is een string met teken (ADR 0010). */
export interface Transactie {
  datum: string;
  tegenpartij: string;
  bedrag: string;
  /** S4.1: de details. Het tijdstip met offset ("2026-10-01T06:02:00+02:00"); iban is null zonder IBAN. */
  type: string;
  tijdstip: string;
  van: Partij;
  naar: Partij;
}

export interface Partij {
  naam: string;
  iban: string | null;
}

/**
 * De betaalrekening: de transacties per datum, in de volgorde van de API; zonder transacties een melding (S2.1).
 * "Toon meer" vraagt om de volgende portie; die komt achteraan de lijst, dus een datum krijgt nooit twee koppen (S2.2).
 * Een klik op een transactie klapt de details uit, een tweede klik klapt ze weer in (S4.1).
 */
@Component({
  selector: 'app-betaalrekening',
  template: `
    <h2>Betaalrekening</h2>
    @if (dagen().length === 0) {
      <p>{{ gezocht() ? 'Geen transacties gevonden.' : 'Nog geen af- of bijschrijvingen.' }}</p>
    } @else {
      <ul aria-label="Transacties">
        @for (dag of dagen(); track dag.datum) {
          <li>
            <h3>{{ dag.kop }}</h3>
            <ul>
              @for (transactie of dag.transacties; track transactie.bron) {
                <li>
                  <button
                    type="button"
                    [attr.aria-expanded]="uitgeklapt() === transactie.bron"
                    (click)="klap(transactie.bron)"
                  >
                    <span>{{ transactie.tegenpartij }}</span>&ngsp;<span>{{ transactie.bedrag }}</span>
                  </button>
                  @if (uitgeklapt() === transactie.bron) {
                    <dl [attr.aria-label]="'Details van ' + transactie.tegenpartij">
                      <dt>Transactietype</dt>
                      <dd>{{ transactie.details.type }}</dd>
                      <dt>Van</dt>
                      <dd>
                        <span>{{ transactie.bron.van.naam }}</span>
                        @if (transactie.bron.van.iban) {
                          &ngsp;<span>{{ transactie.bron.van.iban }}</span>
                        }
                      </dd>
                      <dt>Naar</dt>
                      <dd>
                        <span>{{ transactie.bron.naar.naam }}</span>
                        @if (transactie.bron.naar.iban) {
                          &ngsp;<span>{{ transactie.bron.naar.iban }}</span>
                        }
                      </dd>
                      <dt>Datum transactie</dt>
                      <dd>{{ transactie.details.tijdstip }}</dd>
                      <dt>Uitgevoerd op</dt>
                      <dd>{{ transactie.details.uitgevoerdOp }}</dd>
                    </dl>
                  }
                </li>
              }
            </ul>
          </li>
        }
      </ul>
      @if (meer()) {
        <button type="button" (click)="toonMeer.emit()">Toon meer</button>
      }
    }
  `,
})
export class Betaalrekening {
  readonly transacties = input.required<Transactie[]>();
  /** Of er nog oudere transacties zijn. */
  readonly meer = input(false);
  /** Of de lijst het resultaat van een zoekopdracht is (S5.1); dan heet een lege lijst "Geen transacties gevonden.". */
  readonly gezocht = input(false);
  readonly toonMeer = output();

  /** De transactie waarvan de details open zijn; hooguit één tegelijk. */
  protected readonly uitgeklapt = signal<Transactie | undefined>(undefined);

  protected readonly dagen = computed(() => {
    const dagen: { datum: string; kop: string; transacties: Regel[] }[] = [];
    for (const bron of this.transacties()) {
      if (dagen.at(-1)?.datum !== bron.datum) {
        dagen.push({ datum: bron.datum, kop: datumregel(bron.datum), transacties: [] });
      }
      dagen.at(-1)?.transacties.push({
        bron,
        tegenpartij: bron.tegenpartij,
        bedrag: bedragMetTeken(bron.bedrag),
        details: {
          type: typeVoorWeergave(bron.type),
          tijdstip: tijdstipVoorWeergave(bron.tijdstip),
          uitgevoerdOp: datumZonderDag(bron.datum),
        },
      });
    }
    return dagen;
  });

  protected klap(transactie: Transactie): void {
    this.uitgeklapt.update((open) => (open === transactie ? undefined : transactie));
  }
}

/** Een transactieregel zoals het scherm hem toont. */
interface Regel {
  bron: Transactie;
  tegenpartij: string;
  bedrag: string;
  details: { type: string; tijdstip: string; uitgevoerdOp: string };
}
