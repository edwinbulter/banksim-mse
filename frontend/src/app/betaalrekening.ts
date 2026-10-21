import { Component, computed, input, output } from '@angular/core';
import { datumregel } from './datum/datumregel';
import { bedragMetTeken } from './geld/bedrag';

/** Zoals bank-api hem levert, van nieuw naar oud; het bedrag is een string met teken (ADR 0010). */
export interface Transactie {
  datum: string;
  tegenpartij: string;
  bedrag: string;
}

/**
 * De betaalrekening: de transacties per datum, in de volgorde van de API; zonder transacties een melding (S2.1).
 * "Toon meer" vraagt om de volgende portie; die komt achteraan de lijst, dus een datum krijgt nooit twee koppen (S2.2).
 */
@Component({
  selector: 'app-betaalrekening',
  template: `
    <h2>Betaalrekening</h2>
    @if (dagen().length === 0) {
      <p>Nog geen af- of bijschrijvingen.</p>
    } @else {
      <ul aria-label="Transacties">
        @for (dag of dagen(); track dag.datum) {
          <li>
            <h3>{{ dag.kop }}</h3>
            <ul>
              @for (transactie of dag.transacties; track $index) {
                <li>
                  <span>{{ transactie.tegenpartij }}</span>&ngsp;<span>{{ transactie.bedrag }}</span>
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
  readonly toonMeer = output();

  protected readonly dagen = computed(() => {
    const dagen: { datum: string; kop: string; transacties: { tegenpartij: string; bedrag: string }[] }[] = [];
    for (const { datum, tegenpartij, bedrag } of this.transacties()) {
      if (dagen.at(-1)?.datum !== datum) {
        dagen.push({ datum, kop: datumregel(datum), transacties: [] });
      }
      dagen.at(-1)?.transacties.push({ tegenpartij, bedrag: bedragMetTeken(bedrag) });
    }
    return dagen;
  });
}
