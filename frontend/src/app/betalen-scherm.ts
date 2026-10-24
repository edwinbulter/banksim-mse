import { HttpClient } from '@angular/common/http';
import { Component, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { bedragUitInvoer, bedragVoorWeergave } from './geld/bedrag';

/**
 * Route `/rekeningen/:iban/betalen` (S7.1, FO "Betalen scherm"): bedrag, naam en IBAN van de ontvanger; "Volgende"
 * toont het controlescherm, "Bevestigen" boekt de betaling en keert terug naar de betaalrekening. De controle of de
 * ontvanger een contact is en of het saldo toereikend is, doet de API.
 */
@Component({
  selector: 'app-betalen-scherm',
  template: `
    @if (controle()) {
      <h2>Controleer je betaling</h2>
      <dl>
        <dt>Bedrag</dt>
        <dd>{{ weergave(bedrag()) }}</dd>
        <dt>Naam ontvanger</dt>
        <dd>{{ naam() }}</dd>
        <dt>Rekeningnummer (IBAN)</dt>
        <dd>{{ ontvanger() }}</dd>
      </dl>
      <button type="button" (click)="bevestig()">Bevestigen</button>
    } @else {
      <h2>Betalen</h2>
      <form aria-label="Betalen" (submit)="$event.preventDefault(); controle.set(true)">
        <label for="betalen-bedrag">Bedrag (€)</label>
        <input id="betalen-bedrag" inputmode="decimal" [value]="bedrag()" (input)="bedrag.set(waarde($event))" />
        <label for="betalen-naam">Naam ontvanger</label>
        <input id="betalen-naam" [value]="naam()" (input)="naam.set(waarde($event))" />
        <label for="betalen-iban">Rekeningnummer (IBAN)</label>
        <input id="betalen-iban" [value]="ontvanger()" (input)="ontvanger.set(waarde($event))" />
        <button type="submit">Volgende</button>
      </form>
    }
  `,
})
export class BetalenScherm {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  /** De eigen rekening uit de URL, via withComponentInputBinding. */
  readonly iban = input.required<string>();

  protected readonly bedrag = signal('');
  protected readonly naam = signal('');
  protected readonly ontvanger = signal('');
  protected readonly controle = signal(false);

  protected bevestig(): void {
    const betaling = { van: this.iban(), naar: this.ontvanger(), naam: this.naam(), bedrag: bedragUitInvoer(this.bedrag()) };
    this.http.post('/api/betalingen', betaling).subscribe(() => void this.router.navigate(['/rekeningen', this.iban()]));
  }

  protected weergave(invoer: string): string {
    return bedragVoorWeergave(bedragUitInvoer(invoer));
  }

  protected waarde(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }
}
