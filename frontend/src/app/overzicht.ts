import { Component, computed, input, output } from '@angular/core';
import { bedragVoorWeergave } from './geld/bedrag';

/** Zoals bank-api hem levert; het saldo is een string, bijvoorbeeld "1842.17" (ADR 0010). */
export interface Rekening {
  soort: 'betaal' | 'spaar';
  iban: string;
  saldo: string;
}

/** Het overzicht van de ingelogde klant: de betaalrekeningen met IBAN en saldo (S1.1); het IBAN opent de rekening (S2.1). */
@Component({
  selector: 'app-overzicht',
  template: `
    <h2>Welkom</h2>
    <ul aria-label="Betaalrekeningen">
      @for (rekening of betaalrekeningen(); track rekening.iban) {
        <li>
          <button type="button" (click)="open.emit(rekening.iban)">{{ rekening.iban }}</button>&ngsp;<span>{{ rekening.saldo }}</span>
        </li>
      }
    </ul>
  `,
})
export class Overzicht {
  readonly rekeningen = input.required<Rekening[]>();
  readonly open = output<string>();

  protected readonly betaalrekeningen = computed(() =>
    this.rekeningen()
      .filter((rekening) => rekening.soort === 'betaal')
      .map((rekening) => ({ iban: rekening.iban, saldo: bedragVoorWeergave(rekening.saldo) })),
  );
}
