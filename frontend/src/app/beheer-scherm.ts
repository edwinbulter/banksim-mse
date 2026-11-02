import { HttpClient } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import type { RekeninghouderInBeheer, Simulatiedatum, SimulatiedatumInBeheer } from './api/schemas';
import { datumInTekst } from './datum/datumregel';
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
    <form aria-label="Simulatiedatum zetten" novalidate (submit)="$event.preventDefault(); zetSimulatiedatum()">
      <label for="beheer-simulatiedatum">Simulatiedatum</label>
      <input
        id="beheer-simulatiedatum"
        type="date"
        [value]="simulatiedatum()"
        [min]="historie()?.eerste ?? ''"
        [max]="historie()?.laatste ?? ''"
        (input)="simulatiedatum.set(waarde($event))"
      />
      <button type="submit">Toepassen</button>
      <button type="button" (click)="terugNaarVandaag()">Terug naar vandaag</button>
      @if (beschikbaar(); as beschikbaar) {
        <p>Beschikbaar: {{ beschikbaar }}</p>
      }
    </form>
    @if (status(); as status) {
      <p role="status">{{ status }}</p>
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
  /** S10.2 (FO): de eerste en laatste dag waarop de bank kan staan, zoals de API ze geeft. */
  protected readonly historie = signal<Pick<SimulatiedatumInBeheer, 'eerste' | 'laatste'> | undefined>(undefined);
  /** "1 oktober 2021 t/m 31 december 2026". */
  protected readonly beschikbaar = computed(() => {
    const historie = this.historie();
    return historie && `${datumInTekst(historie.eerste)} t/m ${datumInTekst(historie.laatste)}`;
  });
  /** Wat er na Toepassen of Terug naar vandaag gebeurd is. */
  protected readonly status = signal<string | undefined>(undefined);
  protected readonly tekst = signal('');
  /** Undefined zolang er niet gezocht is. */
  protected readonly gevonden = signal<RekeninghouderInBeheer[] | undefined>(undefined);
  protected readonly melding = signal<string | undefined>(undefined);

  constructor() {
    this.laadSimulatiedatum();
  }

  /**
   * "Toepassen" (FO): de datum geldt direct voor alle gebruikers. Een datum buiten de historie gaat niet naar de bank; die
   * weigert hem ook zelf. Datums als "2026-10-05" vergelijken als tekst in de goede volgorde.
   */
  protected zetSimulatiedatum(): void {
    this.melding.set(undefined);
    this.status.set(undefined);
    const datum = this.simulatiedatum();
    const historie = this.historie();
    if (historie && (datum < historie.eerste || datum > historie.laatste)) {
      this.melding.set(`Kies een datum van ${this.beschikbaar()}.`);
      return;
    }
    this.http.put('/api/beheer/simulatiedatum', { datum: this.simulatiedatum() } satisfies Simulatiedatum).subscribe({
      next: () => this.status.set('Simulatiedatum toegepast.'),
      error: (fout) => this.melding.set(meldingBij(fout)),
    });
  }

  /** "Terug naar vandaag" (FO): daarna toont de datumkiezer de echte datum, zoals de bank hem nu kent. */
  protected terugNaarVandaag(): void {
    this.melding.set(undefined);
    this.status.set(undefined);
    this.http.delete('/api/beheer/simulatiedatum').subscribe({
      next: () => {
        this.status.set('De bank staat weer op vandaag.');
        this.laadSimulatiedatum();
      },
      error: (fout) => this.melding.set(meldingBij(fout)),
    });
  }

  private laadSimulatiedatum(): void {
    this.http.get<SimulatiedatumInBeheer>('/api/beheer/simulatiedatum').subscribe({
      next: ({ datum, eerste, laatste }) => {
        this.simulatiedatum.set(datum);
        this.historie.set({ eerste, laatste });
      },
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
