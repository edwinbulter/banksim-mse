import { HttpClient } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';

/** Walking skeleton: toont de versie van bank-api, als bewijs dat browser → ingress → API werkt. */
@Component({
  selector: 'app-root',
  template: `
    <main>
      <h1>BankSim</h1>
    </main>
    <footer>
      @if (versie(); as versie) {
        Versie {{ versie }}
      }
    </footer>
  `,
})
export class App {
  protected readonly versie = toSignal(
    inject(HttpClient)
      .get<{ versie: string }>('/api/versie')
      .pipe(map((antwoord) => antwoord.versie)),
  );
}
