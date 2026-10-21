import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of, throwError } from 'rxjs';
import { Betaalrekening, type Transactie } from './betaalrekening';
import { Overzicht, type Rekening } from './overzicht';

/**
 * De startpagina. Zonder login (de BFF geeft 401) een link naar de login van de BFF; daarna het overzicht (S1.1).
 * Een klik op een IBAN opent die rekening met de eerste 50 transacties (S2.1); "Toon meer" laadt de volgende 50 met de
 * cursor uit het vorige antwoord (S2.2, ADR 0011).
 * De voettekst toont altijd de versie van bank-api.
 */
@Component({
  selector: 'app-root',
  imports: [Betaalrekening, Overzicht],
  template: `
    <main>
      <h1>BankSim</h1>
      @let overzicht = rekeningen();
      @if (overzicht === 'niet-ingelogd') {
        <a href="/oauth2/authorization/keycloak">Inloggen</a>
      } @else if (geladen(); as geladen) {
        <app-betaalrekening
          [transacties]="geladen.transacties"
          [meer]="geladen.volgende !== null"
          (toonMeer)="toonMeer()"
        />
      } @else if (overzicht) {
        <app-overzicht [rekeningen]="overzicht" (open)="open($event)" />
      }
    </main>
    <footer>
      @if (versie(); as versie) {
        Versie {{ versie }}
      }
    </footer>
  `,
})
export class App {
  private readonly http = inject(HttpClient);

  protected readonly versie = toSignal(
    this.http.get<{ versie: string }>('/api/versie').pipe(map((antwoord) => antwoord.versie)),
  );

  protected readonly rekeningen = toSignal(
    this.http.get<Rekening[]>('/api/rekeningen').pipe(
      catchError((fout: HttpErrorResponse) =>
        fout.status === 401 ? of('niet-ingelogd' as const) : throwError(() => fout),
      ),
    ),
  );

  /** Het IBAN van de geopende rekening. */
  private geopend?: string;

  /** De tot nu toe geladen transacties van de geopende rekening; zonder geopende rekening het overzicht. */
  protected readonly geladen = signal<Pagina | undefined>(undefined);

  protected open(iban: string): void {
    this.geopend = iban;
    this.laad(undefined);
  }

  protected toonMeer(): void {
    this.laad(this.geladen()?.volgende ?? undefined);
  }

  private laad(na: string | undefined): void {
    const pad = `/api/rekeningen/${this.geopend}/transacties`;
    const opties = na === undefined ? {} : { params: { na } };
    this.http.get<Pagina>(pad, opties).subscribe((pagina) =>
      this.geladen.update((eerder) => ({
        transacties: [...(na === undefined ? [] : (eerder?.transacties ?? [])), ...pagina.transacties],
        volgende: pagina.volgende,
      })),
    );
  }
}

/** Een pagina van bank-api; `volgende` is de cursor voor "Toon meer", of null aan het eind (ADR 0011). */
interface Pagina {
  transacties: Transactie[];
  volgende: string | null;
}
