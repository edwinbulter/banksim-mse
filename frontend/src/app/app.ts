import { HttpClient } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterOutlet } from '@angular/router';
import { map } from 'rxjs';
import { Browser } from './browser';
import { Sessie } from './sessie';

/**
 * De kop, de voettekst met de versie van bank-api, en het scherm van de route (ADR 0012). Zodra de BFF een 401 geeft,
 * staat er in plaats van het scherm een link naar de login van de BFF (S1.1). Anders staat in de kop "Uitloggen"
 * (S6.2): de BFF beëindigt zijn sessie en geeft de uitlog-URL van Keycloak, waar de app de browser heen stuurt
 * (ADR 0021).
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  template: `
    <main>
      <h1>BankSim</h1>
      @if (sessie.nietIngelogd()) {
        <a href="/oauth2/authorization/keycloak">Inloggen</a>
      } @else {
        <button type="button" (click)="logUit()">Uitloggen</button>
        <router-outlet />
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
  protected readonly sessie = inject(Sessie);
  private readonly http = inject(HttpClient);
  private readonly browser = inject(Browser);

  /** Angular zet het CSRF-token (ADR 0016) zelf als header op de POST. */
  protected logUit(): void {
    // De BFF geeft altijd een Location bij de 202 (BffTests).
    this.http
      .post('/logout', null, { observe: 'response' })
      .subscribe((antwoord) => this.browser.naar(antwoord.headers.get('Location')!));
  }

  protected readonly versie = toSignal(
    this.http.get<{ versie: string }>('/api/versie').pipe(map((antwoord) => antwoord.versie)),
  );
}
