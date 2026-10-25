import { HttpClient } from '@angular/common/http';
import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import type { Transactie } from './betaalrekening';
import { datumregel } from './datum/datumregel';
import { bedragMetTeken, bedragVoorWeergave } from './geld/bedrag';

/**
 * Route `/spaarrekeningen/:iban` (S8.1, FO "Spaarrekening scherm"): de mutaties per datum, nieuw naar oud, met
 * omschrijving en bedrag, zoals "Rente januari +25,48". Zonder omschrijving staat er de tegenpartij. Bovenaan staat de
 * lopende rente: opgebouwd deze maand, nog niet bijgeschreven. Bij de overgang naar een ouder jaar staat een regel met
 * het jaartal (FO).
 */
@Component({
  selector: 'app-spaarrekening-scherm',
  imports: [RouterLink],
  template: `
    <a routerLink="/">Overzicht</a>
    <h2>Spaarrekening</h2>
    <a [routerLink]="['/spaarrekeningen', iban(), 'opnemen']">Opnemen</a>
    <a [routerLink]="['/spaarrekeningen', iban(), 'inleggen']">Inleggen</a>
    @if (lopendeRente(); as rente) {
      <dl>
        <dt>Lopende rente</dt>
        <dd>{{ rente }}</dd>
      </dl>
    }
    <ul aria-label="Mutaties">
      @for (dag of dagen(); track dag.datum) {
        @if (dag.nieuwJaar) {
          <li>{{ dag.nieuwJaar }}</li>
        }
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

  /** "€ 12,36"; leeg tot het antwoord er is. */
  protected readonly lopendeRente = signal<string | undefined>(undefined);

  protected readonly dagen = computed(() => {
    const dagen: {
      datum: string;
      kop: string;
      nieuwJaar?: string;
      mutaties: { omschrijving: string; bedrag: string }[];
    }[] = [];
    for (const transactie of this.transacties()) {
      const vorige = dagen.at(-1)?.datum;
      if (vorige !== transactie.datum) {
        const jaar = transactie.datum.slice(0, 4);
        const nieuwJaar = vorige && vorige.slice(0, 4) !== jaar ? jaar : undefined;
        dagen.push({ datum: transactie.datum, kop: datumregel(transactie.datum), nieuwJaar, mutaties: [] });
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
      this.http
        .get<{ bedrag: string }>(`/api/rekeningen/${this.iban()}/lopende-rente`)
        .subscribe(({ bedrag }) => this.lopendeRente.set(bedragVoorWeergave(bedrag)));
    });
  }
}
