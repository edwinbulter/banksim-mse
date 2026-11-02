import { BeheerScherm } from './beheer-scherm';
import type { Routes } from '@angular/router';
import { BetaalrekeningScherm } from './betaalrekening-scherm';
import { BetalenScherm } from './betalen-scherm';
import { alleenBeheerder, beheerderNaarBeheer } from './ik';
import { OverschrijvenScherm } from './overschrijven-scherm';
import { OverzichtScherm } from './overzicht-scherm';
import { SpaarrekeningScherm } from './spaarrekening-scherm';

/** Een URL per scherm (ADR 0012): herladen en de terugknop van de browser werken per scherm. */
export const routes: Routes = [
  { path: '', component: OverzichtScherm, canActivate: [beheerderNaarBeheer] },
  { path: 'rekeningen/:iban', component: BetaalrekeningScherm },
  { path: 'rekeningen/:iban/betalen', component: BetalenScherm },
  { path: 'spaarrekeningen/:iban', component: SpaarrekeningScherm },
  { path: 'spaarrekeningen/:iban/inleggen', component: OverschrijvenScherm, data: { richting: 'inleggen' } },
  { path: 'spaarrekeningen/:iban/opnemen', component: OverschrijvenScherm, data: { richting: 'opnemen' } },
  { path: 'beheer', component: BeheerScherm, canActivate: [alleenBeheerder] },
  {
    path: 'beheer/rekeningen/:iban',
    component: BetaalrekeningScherm,
    canActivate: [alleenBeheerder],
    data: { beheer: true },
  },
  { path: '**', redirectTo: '' },
];
