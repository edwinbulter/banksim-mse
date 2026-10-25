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
 * Route `/spaarrekeningen/:iban/inleggen` (S9.1, FO "Overschrijven scherm"): Van is de eigen betaalrekening, Naar de
 * spaarrekening uit de URL. "Overschrijven" boekt met één Idempotency-Key per scherm (ADR 0027) en keert terug naar de
 * spaarrekening. De link "Spaarrekening" is "< Spaarrekening" uit het FO.
 */
@Component({
  selector: 'app-overschrijven-scherm',
  imports: [RouterLink],
  template: `
    <a [routerLink]="['/spaarrekeningen', iban()]">Spaarrekening</a>
    <h2>Overschrijven</h2>
    <form aria-label="Overschrijven" (submit)="$event.preventDefault(); schrijfOver()">
      <section aria-label="Van">Van: Betaalrekening {{ van() }}</section>
      <section aria-label="Naar">Naar: Spaarrekening {{ iban() }}</section>
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

  private readonly rekeningen = signal<Rekening[]>([]);
  protected readonly van = computed(() => this.rekeningen().find((rekening) => rekening.soort === 'betaal')?.iban ?? '');
  protected readonly bedrag = signal('');

  /** ADR 0027: één sleutel per scherm; een dubbelklik stuurt dezelfde. */
  private readonly sleutel = crypto.randomUUID();

  constructor() {
    this.http.get<Rekening[]>('/api/rekeningen').subscribe((rekeningen) => this.rekeningen.set(rekeningen));
  }

  protected schrijfOver(): void {
    const opdracht = { van: this.van(), naar: this.iban(), bedrag: bedragUitInvoer(this.bedrag()) };
    const headers = { 'Idempotency-Key': this.sleutel };
    this.http
      .post('/api/overschrijvingen', opdracht, { headers })
      .subscribe(() => void this.router.navigate(['/spaarrekeningen', this.iban()]));
  }

  protected waarde(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }
}
