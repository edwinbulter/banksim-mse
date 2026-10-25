import type { Routes } from '@angular/router';
import { BetaalrekeningScherm } from './betaalrekening-scherm';
import { BetalenScherm } from './betalen-scherm';
import { OverschrijvenScherm } from './overschrijven-scherm';
import { OverzichtScherm } from './overzicht-scherm';
import { SpaarrekeningScherm } from './spaarrekening-scherm';

/** Een URL per scherm (ADR 0012): herladen en de terugknop van de browser werken per scherm. */
export const routes: Routes = [
  { path: '', component: OverzichtScherm },
  { path: 'rekeningen/:iban', component: BetaalrekeningScherm },
  { path: 'rekeningen/:iban/betalen', component: BetalenScherm },
  { path: 'spaarrekeningen/:iban', component: SpaarrekeningScherm },
  { path: 'spaarrekeningen/:iban/inleggen', component: OverschrijvenScherm },
  { path: '**', redirectTo: '' },
];
