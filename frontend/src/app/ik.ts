import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { type CanActivateFn, Router } from '@angular/router';
import { catchError, map, type Observable, of, shareReplay } from 'rxjs';
import type { Ik } from './api/schemas';

/**
 * ADR 0037: wie de ingelogde gebruiker is, één keer opgevraagd bij bank-api. Alleen voor de navigatie: de API beslist
 * zelf over toegang. Lukt het niet (bijvoorbeeld 401), dan geldt de gebruiker als geen beheerder; de 401 zelf meldt
 * meldNietIngelogd.
 */
@Injectable({ providedIn: 'root' })
export class IkDienst {
  readonly beheerder: Observable<boolean> = inject(HttpClient)
    .get<Ik>('/api/ik')
    .pipe(
      map((ik) => ik.beheerder),
      catchError(() => of(false)),
      shareReplay(1),
    );
}

/** De portier van /beheer: een beheerder mag door, een klant gaat naar zijn overzicht. */
export const alleenBeheerder: CanActivateFn = () => {
  const router = inject(Router);
  return inject(IkDienst).beheerder.pipe(map((beheerder) => beheerder || router.createUrlTree(['/'])));
};
