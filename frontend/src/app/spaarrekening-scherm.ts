import { HttpClient } from '@angular/common/http';
import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import type { Transactie } from './betaalrekening';
import { datumregel } from './datum/datumregel';
import { bedragMetTeken } from './geld/bedrag';

/**
 * Route `/spaarrekeningen/:iban` (S8.1, FO "Spaarrekening scherm"): de mutaties per datum, nieuw naar oud, met
 * omschrijving en bedrag, zoals "Rente januari +25,48". Zonder omschrijving staat er de tegenpartij.
 */
@Component({
  selector: 'app-spaarrekening-scherm',
  imports: [RouterLink],
  template: `
    <a routerLink="/">Overzicht</a>
    <h2>Spaarrekening</h2>
    <ul aria-label="Mutaties">
      @for (dag of dagen(); track dag.datum) {
        <li>
          <h3>{{ dag.kop }}</h3>
          <ul>
            @for (mutatie of dag.mutaties; track $index) {
              <li>{{ mutatie.omschrijving }} {{ mutatie.bedrag }}</li>
            }
          </ul>
        </li>
      }
    </ul>
  `,
})
export class SpaarrekeningScherm {
  private readonly http = inject(HttpClient);

  /** Uit de URL, via withComponentInputBinding. */
  readonly iban = input.required<string>();

  private readonly transacties = signal<Transactie[]>([]);

  protected readonly dagen = computed(() => {
    const dagen: { datum: string; kop: string; mutaties: { omschrijving: string; bedrag: string }[] }[] = [];
    for (const transactie of this.transacties()) {
      if (dagen.at(-1)?.datum !== transactie.datum) {
        dagen.push({ datum: transactie.datum, kop: datumregel(transactie.datum), mutaties: [] });
      }
      dagen.at(-1)?.mutaties.push({
        omschrijving: transactie.omschrijving ?? transactie.tegenpartij,
        bedrag: bedragMetTeken(transactie.bedrag),
      });
    }
    return dagen;
  });

  constructor() {
    effect(() => {
      this.http
        .get<{ transacties: Transactie[] }>(`/api/rekeningen/${this.iban()}/transacties`)
        .subscribe((pagina) => this.transacties.set(pagina.transacties));
    });
  }
}
