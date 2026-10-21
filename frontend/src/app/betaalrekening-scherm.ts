import { HttpClient } from '@angular/common/http';
import { Component, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Betaalrekening, type Transactie } from './betaalrekening';

/**
 * Route `/rekeningen/:iban`: de betaalrekening van het IBAN uit de URL, zodat herladen hem open houdt (S2a.1).
 * Laadt de eerste 50 transacties (S2.1); "Toon meer" laadt de volgende 50 met de cursor uit het vorige antwoord
 * (S2.2, ADR 0011). De link "Overzicht" is "< Overzicht" uit het origineel.
 */
@Component({
  selector: 'app-betaalrekening-scherm',
  imports: [Betaalrekening, RouterLink],
  template: `
    <a routerLink="/">Overzicht</a>
    @if (geladen(); as geladen) {
      <app-betaalrekening
        [transacties]="geladen.transacties"
        [meer]="geladen.volgende !== null"
        (toonMeer)="toonMeer()"
      />
    }
  `,
})
export class BetaalrekeningScherm {
  private readonly http = inject(HttpClient);

  /** Uit de URL, via withComponentInputBinding. */
  readonly iban = input.required<string>();

  /** De tot nu toe geladen transacties. */
  protected readonly geladen = signal<Pagina | undefined>(undefined);

  constructor() {
    effect(() => {
      this.iban();
      this.geladen.set(undefined);
      this.laad(undefined);
    });
  }

  protected toonMeer(): void {
    this.laad(this.geladen()?.volgende ?? undefined);
  }

  private laad(na: string | undefined): void {
    const pad = `/api/rekeningen/${this.iban()}/transacties`;
    const opties = na === undefined ? {} : { params: { na } };
    this.http.get<Pagina>(pad, opties).subscribe({
      next: (pagina) =>
        this.geladen.update((eerder) => ({
          transacties: [...(na === undefined ? [] : (eerder?.transacties ?? [])), ...pagina.transacties],
          volgende: pagina.volgende,
        })),
      // Een 401 meldt de interceptor aan App.
      error: () => undefined,
    });
  }
}

/** Een pagina van bank-api; `volgende` is de cursor voor "Toon meer", of null aan het eind (ADR 0011). */
interface Pagina {
  transacties: Transactie[];
  volgende: string | null;
}
