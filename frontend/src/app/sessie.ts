import { type HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { tap } from 'rxjs';

/** Of de BFF heeft laten weten dat er geen (geldige) sessie is; dan toont App de link "Inloggen". */
@Injectable({ providedIn: 'root' })
export class Sessie {
  readonly nietIngelogd = signal(false);
}

/** Elk scherm laadt zijn eigen gegevens (ADR 0012); een 401 op welk verzoek dan ook betekent: opnieuw inloggen. */
export const meldNietIngelogd: HttpInterceptorFn = (verzoek, volgende) => {
  const sessie = inject(Sessie);
  return volgende(verzoek).pipe(
    tap({
      error: (fout: unknown) => {
        if (fout instanceof HttpErrorResponse && fout.status === 401) sessie.nietIngelogd.set(true);
      },
    }),
  );
};
