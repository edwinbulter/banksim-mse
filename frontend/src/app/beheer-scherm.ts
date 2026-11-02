import { HttpClient } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import type { RekeninghouderInBeheer } from './api/schemas';
import { meldingBij } from './fout';
import { bedragVoorWeergave } from './geld/bedrag';

/**
 * Route `/beheer` (S10.1, FO "Admin scherm"): de beheerder zoekt rekeninghouders op naam of rekeningnummer en ziet
 * hun rekeningen met de saldi op de simulatiedatum. Hoogstens 50 regels; de API zoekt en begrenst.
 */
@Component({
  selector: 'app-beheer-scherm',
  imports: [RouterLink],
  template: `
    <h2>Beheer</h2>
    <form aria-label="Rekeninghouders zoeken" (submit)="$event.preventDefault(); zoek()">
      <label for="beheer-zoek">Naam of rekeningnummer</label>
      <input id="beheer-zoek" [value]="tekst()" (input)="tekst.set(waarde($event))" />
      <button type="submit">Zoeken</button>
    </form>
    @if (melding(); as melding) {
      <p role="alert">{{ melding }}</p>
    }
    @if (gevonden(); as gevonden) {
      @if (gevonden.length === 0) {
        <p>Geen rekeninghouders gevonden.</p>
      } @else {
        <table aria-label="Rekeninghouders">
          <thead>
            <tr><th>Naam</th><th>Betaalrekening</th><th>Saldo</th><th>Spaarrekening</th><th>Spaarsaldo</th></tr>
          </thead>
          <tbody>
            @for (r of gevonden; track r.betaalrekening + (r.spaarrekening ?? '')) {
              <tr>
                <td>{{ r.naam }}</td>
                <td><a [routerLink]="['/beheer/rekeningen', r.betaalrekening]">{{ r.betaalrekening }}</a></td>
                <td>{{ weergave(r.saldo) }}</td>
                <td>{{ r.spaarrekening ?? '' }}</td>
                <td>{{ r.spaarsaldo === null ? '' : weergave(r.spaarsaldo) }}</td>
              </tr>
            }
          </tbody>
        </table>
      }
    }
  `,
})
export class BeheerScherm {
  private readonly http = inject(HttpClient);

  protected readonly tekst = signal('');
  /** Undefined zolang er niet gezocht is. */
  protected readonly gevonden = signal<RekeninghouderInBeheer[] | undefined>(undefined);
  protected readonly melding = signal<string | undefined>(undefined);

  protected zoek(): void {
    this.melding.set(undefined);
    this.http.get<RekeninghouderInBeheer[]>('/api/beheer/rekeninghouders', { params: { zoek: this.tekst() } }).subscribe({
      next: (rekeninghouders) => this.gevonden.set(rekeninghouders),
      error: (fout) => this.melding.set(meldingBij(fout)),
    });
  }

  protected weergave(bedrag: string): string {
    return bedragVoorWeergave(bedrag);
  }

  protected waarde(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }
}
