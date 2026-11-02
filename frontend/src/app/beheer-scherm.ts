import { HttpClient } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import type { RekeninghouderInBeheer, Simulatiedatum } from './api/schemas';
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
    <form aria-label="Simulatiedatum zetten" (submit)="$event.preventDefault(); zetSimulatiedatum()">
      <label for="beheer-simulatiedatum">Simulatiedatum</label>
      <input id="beheer-simulatiedatum" type="date" [value]="simulatiedatum()" (input)="simulatiedatum.set(waarde($event))" />
      <button type="submit">Toepassen</button>
    </form>
    @if (toegepast()) {
      <p role="status">Simulatiedatum toegepast.</p>
    }
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
                <td>
                  @if (r.spaarrekening; as spaar) {
                    <a [routerLink]="['/beheer/spaarrekeningen', spaar]">{{ spaar }}</a>
                  }
                </td>
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

  /** S10.2: de datum waarop de bank staat, als "2026-10-05"; leeg tot hij geladen is. */
  protected readonly simulatiedatum = signal('');
  protected readonly toegepast = signal(false);
  protected readonly tekst = signal('');
  /** Undefined zolang er niet gezocht is. */
  protected readonly gevonden = signal<RekeninghouderInBeheer[] | undefined>(undefined);
  protected readonly melding = signal<string | undefined>(undefined);

  constructor() {
    this.http.get<Simulatiedatum>('/api/beheer/simulatiedatum').subscribe({
      next: ({ datum }) => this.simulatiedatum.set(datum),
      error: (fout) => this.melding.set(meldingBij(fout)),
    });
  }

  /** "Toepassen" (FO): de datum geldt direct voor alle gebruikers. */
  protected zetSimulatiedatum(): void {
    this.melding.set(undefined);
    this.toegepast.set(false);
    this.http.put('/api/beheer/simulatiedatum', { datum: this.simulatiedatum() } satisfies Simulatiedatum).subscribe({
      next: () => this.toegepast.set(true),
      error: (fout) => this.melding.set(meldingBij(fout)),
    });
  }

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
