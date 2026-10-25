import { HttpClient } from '@angular/common/http';
import { Component, computed, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { bedragUitInvoer } from './geld/bedrag';

/** Een rekening zoals GET /api/rekeningen hem levert. */
interface Rekening {
  soort: 'betaal' | 'spaar';
  iban: string;
  saldo: string;
}

/**
 * Routes `/spaarrekeningen/:iban/inleggen` en `/opnemen` (S9.1, FO "Overschrijven scherm"): bij inleggen is Van de eigen
 * betaalrekening en Naar de spaarrekening uit de URL, bij opnemen andersom. "Overschrijven" boekt met één Idempotency-Key per scherm (ADR 0027) en keert terug naar de
 * spaarrekening. De link "Spaarrekening" is "< Spaarrekening" uit het FO.
 */
@Component({
  selector: 'app-overschrijven-scherm',
  imports: [RouterLink],
  template: `
    <a [routerLink]="['/spaarrekeningen', iban()]">Spaarrekening</a>
    <h2>Overschrijven</h2>
    <form aria-label="Overschrijven" (submit)="$event.preventDefault(); schrijfOver()">
      <section aria-label="Van">Van: {{ van().naam }} {{ van().iban }}</section>
      <section aria-label="Naar">Naar: {{ naar().naam }} {{ naar().iban }}</section>
      <label for="overschrijven-bedrag">Bedrag (€)</label>
      <input id="overschrijven-bedrag" inputmode="decimal" [value]="bedrag()" (input)="bedrag.set(waarde($event))" />
      <button type="submit">Overschrijven</button>
    </form>
  `,
})
export class OverschrijvenScherm {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  /** De spaarrekening uit de URL, via withComponentInputBinding. */
  readonly iban = input.required<string>();

  /** Uit de routedata: inleggen gaat naar de spaarrekening, opnemen komt ervan (FO). */
  readonly richting = input<'inleggen' | 'opnemen'>('inleggen');

  private readonly rekeningen = signal<Rekening[]>([]);
  private readonly betaal = computed(() => ({
    naam: 'Betaalrekening',
    iban: this.rekeningen().find((rekening) => rekening.soort === 'betaal')?.iban ?? '',
  }));
  private readonly spaar = computed(() => ({ naam: 'Spaarrekening', iban: this.iban() }));
  protected readonly van = computed(() => (this.richting() === 'inleggen' ? this.betaal() : this.spaar()));
  protected readonly naar = computed(() => (this.richting() === 'inleggen' ? this.spaar() : this.betaal()));
  protected readonly bedrag = signal('');

  /** ADR 0027: één sleutel per scherm; een dubbelklik stuurt dezelfde. */
  private readonly sleutel = crypto.randomUUID();

  constructor() {
    this.http.get<Rekening[]>('/api/rekeningen').subscribe((rekeningen) => this.rekeningen.set(rekeningen));
  }

  protected schrijfOver(): void {
    const opdracht = { van: this.van().iban, naar: this.naar().iban, bedrag: bedragUitInvoer(this.bedrag()) };
    const headers = { 'Idempotency-Key': this.sleutel };
    this.http
      .post('/api/overschrijvingen', opdracht, { headers })
      .subscribe(() => void this.router.navigate(['/spaarrekeningen', this.iban()]));
  }

  protected waarde(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }
}
