import { HttpClient } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterOutlet } from '@angular/router';
import { map } from 'rxjs';
import { Sessie } from './sessie';

/**
 * De kop, de voettekst met de versie van bank-api, en het scherm van de route (ADR 0012). Zodra de BFF een 401 geeft,
 * staat er in plaats van het scherm een link naar de login van de BFF (S1.1).
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

  protected readonly versie = toSignal(
    inject(HttpClient).get<{ versie: string }>('/api/versie').pipe(map((antwoord) => antwoord.versie)),
  );
}
