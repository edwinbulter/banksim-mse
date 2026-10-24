import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Betaalrekening, type Transactie } from './betaalrekening';
import { ZoekFormulier, type Zoekopdracht } from './zoek-formulier';

/**
 * Route `/rekeningen/:iban`: de betaalrekening van het IBAN uit de URL, zodat herladen hem open houdt (S2a.1).
 * Laadt de eerste 50 transacties (S2.1); "Toon meer" laadt de volgende 50 met de cursor uit het vorige antwoord
 * (S2.2, ADR 0011). De link "Overzicht" is "< Overzicht" uit het origineel.
 * Zoeken (S5.1) vervangt de lijst door wat gevonden is; "Toon meer" stuurt de zoekvelden opnieuw mee, "Wissen" laadt
 * weer alles. Een geweigerde zoekopdracht (400) toont de melding van de API in het formulier.
 * Een rekening die niet bestaat of van een ander is (404, S6.1), is voor de klant hetzelfde: "Rekening is niet gevonden.".
 */
@Component({
  selector: 'app-betaalrekening-scherm',
  imports: [Betaalrekening, RouterLink, ZoekFormulier],
  template: `
    <a routerLink="/">Overzicht</a>
    @if (nietGevonden()) {
      <p role="alert">Rekening is niet gevonden.</p>
    } @else {
      <app-zoek-formulier [melding]="melding()" (zoek)="zoek($event)" (wis)="zoek({})" />
      @if (geladen(); as geladen) {
        <app-betaalrekening
          [transacties]="geladen.transacties"
          [meer]="geladen.volgende !== null"
          [gezocht]="gezocht()"
          (toonMeer)="toonMeer()"
        />
      }
    }
  `,
})
export class BetaalrekeningScherm {
  private readonly http = inject(HttpClient);

  /** Uit de URL, via withComponentInputBinding. */
  readonly iban = input.required<string>();

  /** De tot nu toe geladen transacties. */
  protected readonly geladen = signal<Pagina | undefined>(undefined);

  /** De zoekvelden van de laatste zoekopdracht; leeg is alles. */
  private filter: Zoekopdracht = {};
  protected readonly gezocht = signal(false);
  protected readonly melding = signal<string | undefined>(undefined);
  protected readonly nietGevonden = signal(false);

  constructor() {
    effect(() => {
      this.iban();
      this.geladen.set(undefined);
      this.laad(undefined);
    });
  }

  protected zoek(opdracht: Zoekopdracht): void {
    this.filter = opdracht;
    this.gezocht.set(Object.keys(opdracht).length > 0);
    this.laad(undefined);
  }

  protected toonMeer(): void {
    this.laad(this.geladen()?.volgende ?? undefined);
  }

  private laad(na: string | undefined): void {
    const pad = `/api/rekeningen/${this.iban()}/transacties`;
    const params = { ...this.filter, ...(na === undefined ? {} : { na }) };
    this.http.get<Pagina>(pad, { params }).subscribe({
      next: (pagina) => {
        this.melding.set(undefined);
        this.geladen.update((eerder) => ({
          transacties: [...(na === undefined ? [] : (eerder?.transacties ?? [])), ...pagina.transacties],
          volgende: pagina.volgende,
        }));
      },
      // Een 400 is een geweigerde zoekopdracht (RFC 9457); een 401 meldt de interceptor aan App.
      error: (fout: HttpErrorResponse) => {
        if (fout.status === 400) this.melding.set((fout.error as { detail?: string } | null)?.detail);
        if (fout.status === 404) this.nietGevonden.set(true);
      },
    });
  }
}

/** Een pagina van bank-api; `volgende` is de cursor voor "Toon meer", of null aan het eind (ADR 0011). */
interface Pagina {
  transacties: Transactie[];
  volgende: string | null;
}
