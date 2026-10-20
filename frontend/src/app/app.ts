import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of, throwError } from 'rxjs';
import { Overzicht, type Rekening } from './overzicht';

/**
 * De startpagina. Zonder login (de BFF geeft 401) een link naar de login van de BFF; daarna het overzicht (S1.1).
 * De voettekst toont altijd de versie van bank-api.
 */
@Component({
  selector: 'app-root',
  imports: [Overzicht],
  template: `
    <main>
      <h1>BankSim</h1>
      @let overzicht = rekeningen();
      @if (overzicht === 'niet-ingelogd') {
        <a href="/oauth2/authorization/keycloak">Inloggen</a>
      } @else if (overzicht) {
        <app-overzicht [rekeningen]="overzicht" />
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
}
