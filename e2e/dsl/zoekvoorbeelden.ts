import type { Boeking } from './klant';

/**
 * S5.1: zoeken in de transacties op tekst (naam, bedrag, IBAN of omschrijving), bedrag, type en richting
 * (doc/werkwijze-mse.md, slice 5).
 * De transacties van de zoekvoorbeelden, zodat je ziet waarom een voorbeeld deze uitkomst heeft (zoeken.spec.ts,
 * beheer.spec.ts).
 */
export const TRANSACTIES: Boeking[] = [
  { op: '2026-10-01T06:02', bedrag: '-812.40', type: 'INCASSO', tegenpartij: 'Woningstichting Thuis', tegenIban: 'NL91ABNA0417164300' },
  { op: '2026-10-02T09:15', bedrag: '3215.00', type: 'ONLINE_BANKIEREN', tegenpartij: 'Werkgever B.V.', tegenIban: 'NL20INGB0001234567' },
  { op: '2026-10-03T13:26', bedrag: '-63.48', type: 'BETAALAUTOMAAT', tegenpartij: 'Albert Heijn' },
  { op: '2026-10-04T20:10', bedrag: '-27.95', type: 'IDEAL_WERO', tegenpartij: 'Albert Heijn Online', tegenIban: 'NL44RABO0123456789' },
  { op: '2026-10-05T07:00', bedrag: '-95.00', type: 'INCASSO', tegenpartij: 'Vattenfall', tegenIban: 'NL71INGB0009876543' },
  { op: '2026-10-06T18:30', bedrag: '50.00', type: 'ONLINE_BANKIEREN', tegenpartij: 'Piet', tegenIban: 'NL07ABNA0555666777' },
];
