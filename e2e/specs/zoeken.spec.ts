import { test } from '../dsl/fixtures';
import type { Boeking } from '../dsl/klant';

/**
 * S5.1: zoeken in de transacties op tekst (naam of IBAN), bedrag, type en richting (doc/werkwijze-mse.md, slice 5).
 * De transacties staan hier, zodat je ziet waarom een voorbeeld deze uitkomst heeft.
 */
const TRANSACTIES: Boeking[] = [
  { op: '2026-10-01T06:02', bedrag: '-812.40', type: 'INCASSO', tegenpartij: 'Woningstichting Thuis', tegenIban: 'NL91ABNA0417164300' },
  { op: '2026-10-02T09:15', bedrag: '3215.00', type: 'ONLINE_BANKIEREN', tegenpartij: 'Werkgever B.V.', tegenIban: 'NL20INGB0001234567' },
  { op: '2026-10-03T13:26', bedrag: '-63.48', type: 'BETAALAUTOMAAT', tegenpartij: 'Albert Heijn' },
  { op: '2026-10-04T20:10', bedrag: '-27.95', type: 'IDEAL_WERO', tegenpartij: 'Albert Heijn Online', tegenIban: 'NL44RABO0123456789' },
  { op: '2026-10-05T07:00', bedrag: '-95.00', type: 'INCASSO', tegenpartij: 'Vattenfall', tegenIban: 'NL71INGB0009876543' },
  { op: '2026-10-06T18:30', bedrag: '50.00', type: 'ONLINE_BANKIEREN', tegenpartij: 'Piet', tegenIban: 'NL07ABNA0555666777' },
];

const voorbeelden = [
  { criterium: 'tekst "albert"', zoek: { tekst: 'albert' }, gevonden: ['Albert Heijn Online', 'Albert Heijn'] },
  { criterium: 'IBAN met spaties', zoek: { tekst: 'NL91 ABNA 0417 1643 00' }, gevonden: ['Woningstichting Thuis'] },
  { criterium: 'bedrag van 50 t/m 100', zoek: { van: '50', tm: '100' }, gevonden: ['Piet', 'Vattenfall', 'Albert Heijn'] },
  { criterium: 'bedrag met komma 27,95 t/m 27,95', zoek: { van: '27,95', tm: '27,95' }, gevonden: ['Albert Heijn Online'] },
  { criterium: 'type Incasso', zoek: { type: 'INCASSO' }, gevonden: ['Vattenfall', 'Woningstichting Thuis'] },
  { criterium: 'inkomende transacties', zoek: { richting: 'inkomend' }, gevonden: ['Piet', 'Werkgever B.V.'] },
  { criterium: 'type Incasso en bedrag t/m 100', zoek: { type: 'INCASSO', tm: '100' }, gevonden: ['Vattenfall'] },
  { criterium: 'tekst "Jumbo"', zoek: { tekst: 'Jumbo' }, gevonden: [] },
] as const;

for (const v of voorbeelden) {
  test(`Zoeken op ${v.criterium}`, async ({ bank }) => {
    const jan = bank.klant('Jan');
    await bank.gegeven('Jan heeft een betaalrekening met saldo € 1.000,00 en de 6 transacties hierboven', async () => {
      await jan.heeftBetaalrekening({ saldo: '1000.00' });
      await jan.heeftBoekingen(TRANSACTIES);
    });
    await bank.als(`Jan zoekt op ${v.criterium}`, () => jan.zoekt(v.zoek));
    await bank.dan(
      v.gevonden.length === 0 ? 'vindt Jan niets' : `vindt Jan ${v.gevonden.join(', ')}`,
      () => jan.verwachtGevonden([...v.gevonden]),
    );
  });
}

test('Ongeldig bedragbereik', async ({ bank }) => {
  const jan = bank.klant('Jan');
  await bank.gegeven('Jan heeft een betaalrekening met saldo € 1.000,00', () =>
    jan.heeftBetaalrekening({ saldo: '1000.00' }),
  );
  await bank.als('Jan zoekt met bedrag van 100 t/m 50', () => jan.zoekt({ van: '100', tm: '50' }));
  await bank.dan('krijgt Jan een melding over het bereik', () => jan.verwachtWeigering('ongeldig-bedragbereik'));
});

test('Zoekformulier openen en wissen', { tag: '@ui' }, async ({ bank }) => {
  const jan = bank.klant('Jan');
  await bank.gegeven('Jan heeft een betaalrekening met saldo € 1.000,00 en de 6 transacties hierboven', async () => {
    await jan.heeftBetaalrekening({ saldo: '1000.00' });
    await jan.heeftBoekingen(TRANSACTIES);
  });
  await bank.als('Jan "Zoeken in transacties" kiest', () => jan.opentZoeken());
  await bank.dan('heet de knop "Verberg zoeken"', () => jan.verwachtZoekknop('Verberg zoeken'));
  await bank.als('Jan zoekt op "albert" en daarna "Wissen" kiest', () => jan.zoektEnWist({ tekst: 'albert' }));
  await bank.dan('zijn alle velden leeg en is "Alle transacties" gekozen', () => jan.verwachtLeegZoekformulier());
  await bank.en('ziet Jan weer alle 6 transacties', () =>
    jan.verwachtGevonden(['Piet', 'Vattenfall', 'Albert Heijn Online', 'Albert Heijn', 'Werkgever B.V.', 'Woningstichting Thuis']),
  );
});
