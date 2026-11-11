import { HttpClient } from '@angular/common/http';
import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import type { LopendeRente, Pagina, Transactie } from './api/schemas';
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
    <p class="terug">
      @if (beheer()) {
        <a routerLink="/beheer">Beheer</a>
      } @else {
        <a routerLink="/">Overzicht</a>
      }
    </p>
    <header class="rekening-kop">
      <h2 class="titel">Spaarrekening</h2>
      @if (lopendeRente(); as rente) {
        <dl class="rekening-kop__rente">
          <dt>Lopende rente</dt>
          <dd class="bedrag">{{ rente }}</dd>
        </dl>
      }
    </header>
    @if (!beheer()) {
      <div class="knoppen knoppen--links">
        <a class="knop" [routerLink]="['/spaarrekeningen', iban(), 'opnemen']">Opnemen</a>
        <a class="knop" [routerLink]="['/spaarrekeningen', iban(), 'inleggen']">Inleggen</a>
      </div>
    }
    <ul class="dagen" aria-label="Mutaties">
      @for (dag of dagen(); track dag.datum) {
        @if (dag.nieuwJaar) {
          <li class="jaarregel">{{ dag.nieuwJaar }}</li>
        }
        <li>
          <h3 class="datumregel">{{ dag.kop }}</h3>
          <ul class="transacties">
            @for (mutatie of dag.mutaties; track $index) {
              <li class="transactie mutatie">
                <span class="transactie__naam">{{ mutatie.omschrijving }}</span>&ngsp;<span class="bedrag">{{
                  mutatie.bedrag
                }}</span>
              </li>
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
  /** Uit de data van de route: de beheerder leest alleen, via /api/beheer, zonder Opnemen en Inleggen (ADR 0038). */
  readonly beheer = input(false);

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
        .get<Pagina>(`${this.api()}/rekeningen/${this.iban()}/transacties`)
        .subscribe((pagina) => this.transacties.set(pagina.transacties));
      this.http
        .get<LopendeRente>(`${this.api()}/rekeningen/${this.iban()}/lopende-rente`)
        .subscribe(({ bedrag }) => this.lopendeRente.set(bedragVoorWeergave(bedrag)));
    });
  }

  private api(): string {
    return this.beheer() ? '/api/beheer' : '/api';
  }
}
