import { HttpClient } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { catchError, of } from 'rxjs';
import { Overzicht, type Rekening } from './overzicht';

/** Route `/`: laadt de rekeningen; een klik op een IBAN gaat naar de URL van die rekening (S1.1, S2a.1). */
@Component({
  selector: 'app-overzicht-scherm',
  imports: [Overzicht],
  template: `
    @if (rekeningen(); as rekeningen) {
      <app-overzicht [rekeningen]="rekeningen" (open)="open($event)" />
    }
  `,
})
export class OverzichtScherm {
  private readonly router = inject(Router);

  /** Bij een fout blijft het scherm leeg; een 401 meldt de interceptor aan App. */
  protected readonly rekeningen = toSignal(
    inject(HttpClient).get<Rekening[]>('/api/rekeningen').pipe(catchError(() => of(undefined))),
  );

  protected open(iban: string): void {
    void this.router.navigate(['/rekeningen', iban]);
  }
}
