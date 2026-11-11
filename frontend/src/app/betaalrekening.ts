import { Component, computed, input, output, signal } from '@angular/core';
import type { Transactie } from './api/schemas';
import { datumregel } from './datum/datumregel';
import { datumZonderDag, tijdstipVoorWeergave } from './datum/tijdstip';
import { bedragMetTeken } from './geld/bedrag';
import { typeVoorWeergave } from './transactie/transactietype';

/**
 * De betaalrekening: de transacties per datum, in de volgorde van de API; zonder transacties een melding (S2.1).
 * "Toon meer" vraagt om de volgende portie; die komt achteraan de lijst, dus een datum krijgt nooit twee koppen (S2.2).
 * Een klik op een transactie klapt de details uit, een tweede klik klapt ze weer in (S4.1).
 */
@Component({
  selector: 'app-betaalrekening',
  template: `
    <h2 class="titel">Betaalrekening</h2>
    @if (dagen().length === 0) {
      <p class="leeg">{{ gezocht() ? 'Geen transacties gevonden.' : 'Nog geen af- of bijschrijvingen.' }}</p>
    } @else {
      <ul class="dagen" aria-label="Transacties">
        @for (dag of dagen(); track dag.datum) {
          <li>
            <h3 class="datumregel">{{ dag.kop }}</h3>
            <ul class="transacties">
              @for (transactie of dag.transacties; track transactie.bron) {
                <li class="transactie" [class.transactie--open]="uitgeklapt() === transactie.bron">
                  <button
                    type="button"
                    class="transactie__regel"
                    [attr.aria-expanded]="uitgeklapt() === transactie.bron"
                    (click)="klap(transactie.bron)"
                  >
                    <span class="transactie__naam">{{ transactie.tegenpartij }}</span>&ngsp;<span
                      class="bedrag"
                      [class.bedrag--af]="transactie.af"
                      [class.bedrag--bij]="!transactie.af"
                      >{{ transactie.bedrag }}</span
                    >
                  </button>
                  @if (uitgeklapt() === transactie.bron) {
                    <dl class="details" [attr.aria-label]="'Details van ' + transactie.tegenpartij">
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
                      @if (transactie.bron.omschrijving; as omschrijving) {
                        <dt>Omschrijving</dt>
                        <dd>{{ omschrijving }}</dd>
                      }
                      @if (transactie.bron.betalingskenmerk; as betalingskenmerk) {
                        <dt>Betalingskenmerk</dt>
                        <dd>{{ betalingskenmerk }}</dd>
                      }
                    </dl>
                  }
                </li>
              }
            </ul>
          </li>
        }
      </ul>
      @if (meer()) {
        <div class="toon-meer">
          <button type="button" class="knop knop--secundair" (click)="toonMeer.emit()">Toon meer</button>
        </div>
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
        af: bron.bedrag.startsWith('-'),
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
  /** Een afschrijving; het scherm toont het bedrag dan in --kleur-af, anders in --kleur-bij. */
  af: boolean;
  details: { type: string; tijdstip: string; uitgevoerdOp: string };
}
