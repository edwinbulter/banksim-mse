import { Component, computed, input, output } from '@angular/core';
import type { Rekening } from './api/schemas';
import { bedragVoorWeergave } from './geld/bedrag';

/**
 * Het overzicht van de ingelogde klant: de betaalrekeningen met IBAN en saldo (S1.1); het IBAN opent de rekening (S2.1).
 * S8.2: daaronder de spaarrekeningen; het IBAN opent het spaarrekeningscherm (S8.1).
 */
@Component({
  selector: 'app-overzicht',
  template: `
    <h2 class="titel">Welkom</h2>
    <ul class="rekeningen" aria-label="Betaalrekeningen">
      @for (rekening of betaalrekeningen(); track rekening.iban) {
        <li class="rekening-kaart">
          <button type="button" class="rekening-kaart__iban" (click)="open.emit(rekening.iban)">{{ rekening.iban }}</button
          >&ngsp;<span class="rekening-kaart__saldo bedrag">{{ rekening.saldo }}</span>
        </li>
      }
    </ul>
    @if (spaarrekeningen().length > 0) {
      <ul class="rekeningen" aria-label="Spaarrekeningen">
        @for (rekening of spaarrekeningen(); track rekening.iban) {
          <li class="rekening-kaart">
            <button type="button" class="rekening-kaart__iban" (click)="openSpaar.emit(rekening.iban)">{{ rekening.iban }}</button
            >&ngsp;<span class="rekening-kaart__saldo bedrag">{{ rekening.saldo }}</span>
          </li>
        }
      </ul>
    }
  `,
})
export class Overzicht {
  readonly rekeningen = input.required<Rekening[]>();
  readonly open = output<string>();
  /** S8.1: een klik op een spaarrekening. */
  readonly openSpaar = output<string>();

  protected readonly betaalrekeningen = computed(() => this.vanSoort('betaal'));
  protected readonly spaarrekeningen = computed(() => this.vanSoort('spaar'));

  private vanSoort(soort: Rekening['soort']): { iban: string; saldo: string }[] {
    return this.rekeningen()
      .filter((rekening) => rekening.soort === soort)
      .map((rekening) => ({ iban: rekening.iban, saldo: bedragVoorWeergave(rekening.saldo) }));
  }
}
