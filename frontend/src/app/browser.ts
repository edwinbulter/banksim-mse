import { DOCUMENT, inject, Injectable } from '@angular/core';

/**
 * Navigeren buiten de app, bijvoorbeeld naar Keycloak (ADR 0021). Een echte grens, zoals de klok of het netwerk: in de
 * tests vervangen, en gedekt door het scenario "Uitloggen".
 */
@Injectable({ providedIn: 'root' })
export class Browser {
  private readonly document = inject(DOCUMENT);

  naar(url: string): void {
    this.document.location.assign(url);
  }
}
