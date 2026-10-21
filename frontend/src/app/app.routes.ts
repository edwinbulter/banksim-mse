import type { Routes } from '@angular/router';
import { BetaalrekeningScherm } from './betaalrekening-scherm';
import { OverzichtScherm } from './overzicht-scherm';

/** Een URL per scherm (ADR 0012): herladen en de terugknop van de browser werken per scherm. */
export const routes: Routes = [
  { path: '', component: OverzichtScherm },
  { path: 'rekeningen/:iban', component: BetaalrekeningScherm },
  { path: '**', redirectTo: '' },
];
