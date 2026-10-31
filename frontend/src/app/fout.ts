import type { HttpErrorResponse } from '@angular/common/http';

/**
 * De melding voor de klant bij een mislukt verzoek: die van de API bij een weigering (RFC 9457), anders een algemene
 * (backlog fase 3.5), zodat een 500 of een netwerkfout niet onzichtbaar blijft.
 */
export function meldingBij(fout: HttpErrorResponse): string {
  return (fout.error as { detail?: string } | null)?.detail ?? 'Er ging iets mis. Probeer het later opnieuw.';
}
