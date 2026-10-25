import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { bedragUitInvoer, bedragVoorWeergave } from './geld/bedrag';

/**
 * Route `/rekeningen/:iban/betalen` (S7.1, FO "Betalen scherm"): bedrag, naam en IBAN van de ontvanger; "Volgende"
 * toont het controlescherm, "Bevestigen" boekt de betaling en keert terug naar de betaalrekening. De controle of de
 * ontvanger een contact is en of het saldo toereikend is, doet de API; een weigering (422) toont haar melding.
 * De link "Betaalrekening" is "< Betaalrekening" uit het FO. Bij typen in naam of IBAN verschijnen suggesties uit de
 * contactenlijst; kiezen vult beide velden.
 */
@Component({
  selector: 'app-betalen-scherm',
  imports: [RouterLink],
  template: `
    <a [routerLink]="['/rekeningen', iban()]">Betaalrekening</a>
    @if (controle()) {
      <h2>Controleer je betaling</h2>
      <dl>
        <dt>Bedrag</dt>
        <dd>{{ weergave(bedrag()) }}</dd>
        <dt>Naam ontvanger</dt>
        <dd>{{ naam() }}</dd>
        <dt>Rekeningnummer (IBAN)</dt>
        <dd>{{ ontvanger() }}</dd>
        @if (omschrijving()) {
          <dt>Omschrijving</dt>
          <dd>{{ omschrijving() }}</dd>
        }
        @if (betalingskenmerk()) {
          <dt>Betalingskenmerk</dt>
          <dd>{{ betalingskenmerk() }}</dd>
        }
      </dl>
      @if (melding(); as melding) {
        <p role="alert">{{ melding }}</p>
      }
      <button type="button" (click)="bevestig()">Bevestigen</button>
    } @else {
      <h2>Betalen</h2>
      <form aria-label="Betalen" (submit)="$event.preventDefault(); naarControle()">
        <label for="betalen-bedrag">Bedrag (€)</label>
        <input id="betalen-bedrag" inputmode="decimal" [value]="bedrag()" (input)="bedrag.set(waarde($event))" />
        <label for="betalen-naam">Naam ontvanger</label>
        <input id="betalen-naam" maxlength="70" [value]="naam()" (input)="naam.set(waarde($event)); zoekContacten(naam())" />
        <label for="betalen-iban">Rekeningnummer (IBAN)</label>
        <input id="betalen-iban" maxlength="34" [value]="ontvanger()" (input)="ontvanger.set(waarde($event)); zoekContacten(ontvanger())" />
        @if (suggesties().length > 0) {
          <ul aria-label="Suggesties">
            @for (contact of suggesties(); track contact.iban) {
              <li>
                <button type="button" (click)="kies(contact)">{{ contact.naam }} {{ contact.iban }}</button>
              </li>
            }
          </ul>
        }
        <label for="betalen-omschrijving">Omschrijving</label>
        <input id="betalen-omschrijving" maxlength="140" [value]="omschrijving()" (input)="omschrijving.set(waarde($event))" />
        <label for="betalen-kenmerk">Betalingskenmerk</label>
        <input id="betalen-kenmerk" maxlength="25" [value]="betalingskenmerk()" (input)="betalingskenmerk.set(waarde($event))" />
        <label for="betalen-extra">Extra omschrijving</label>
        <input id="betalen-extra" maxlength="35" [value]="extraOmschrijving()" (input)="extraOmschrijving.set(waarde($event))" />
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
  /** Optioneel (FO); leeg gaat niet mee naar de API. */
  protected readonly omschrijving = signal('');
  protected readonly betalingskenmerk = signal('');
  protected readonly extraOmschrijving = signal('');
  protected readonly controle = signal(false);
  /** De melding van de API bij een geweigerde betaling (RFC 9457), zoals "Naar dit rekeningnummer kan niet worden betaald.". */
  protected readonly melding = signal<string | undefined>(undefined);

  /** FO: suggesties uit de contactenlijst bij typen in naam of IBAN; de API geeft er hoogstens 10. */
  protected readonly suggesties = signal<Contact[]>([]);

  protected zoekContacten(tekst: string): void {
    this.http.get<Contact[]>('/api/contacten', { params: { q: tekst } }).subscribe((contacten) => this.suggesties.set(contacten));
  }

  /** Kiezen vult naam en IBAN in één keer, en sluit de suggesties. */
  protected kies(contact: Contact): void {
    this.naam.set(contact.naam);
    this.ontvanger.set(contact.iban);
    this.suggesties.set([]);
  }

  /** ADR 0027: één sleutel per controlescherm; elke bevestiging daarvandaan, ook een dubbelklik, stuurt dezelfde. */
  private sleutel = '';

  protected naarControle(): void {
    this.sleutel = crypto.randomUUID();
    this.controle.set(true);
  }

  protected bevestig(): void {
    const betaling = {
      van: this.iban(),
      naar: this.ontvanger(),
      naam: this.naam(),
      bedrag: bedragUitInvoer(this.bedrag()),
      omschrijving: this.omschrijving() || undefined,
      betalingskenmerk: this.betalingskenmerk() || undefined,
      extraOmschrijving: this.extraOmschrijving() || undefined,
    };
    const headers = { 'Idempotency-Key': this.sleutel };
    this.http.post('/api/betalingen', betaling, { headers }).subscribe({
      next: () => void this.router.navigate(['/rekeningen', this.iban()]),
      // Een 422 is een geweigerde betaling met een melding; een 401 meldt de interceptor aan App.
      error: (fout: HttpErrorResponse) => this.melding.set((fout.error as { detail?: string } | null)?.detail),
    });
  }

  protected weergave(invoer: string): string {
    return bedragVoorWeergave(bedragUitInvoer(invoer));
  }

  protected waarde(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }
}

/** Een contact uit de contactenlijst van de bank, zoals `GET /api/contacten` het levert. */
interface Contact {
  naam: string;
  iban: string;
}
