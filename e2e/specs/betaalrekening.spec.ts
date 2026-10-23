import { test } from '../dsl/fixtures';

/** S2.1: de transacties van de betaalrekening, per datum en van nieuw naar oud (doc/werkwijze-mse.md, slice 2). */
test('Transacties per datum, nieuw naar oud, met teken', async ({ bank }) => {
  const jan = bank.klant('Jan');
  await bank.gegeven('Jan heeft een betaalrekening met saldo € 1.000,00', () =>
    jan.heeftBetaalrekening({ saldo: '1000.00' }),
  );
  await bank.en('op 1 oktober 2026 betaalde Jan € 812,40 aan Woningstichting Thuis', () =>
    jan.heeftBetaald({ op: '2026-10-01', bedrag: '812.40', aan: 'Woningstichting Thuis' }),
  );
  await bank.en('op 2 oktober 2026 ontving Jan € 3.215,00 van Werkgever B.V.', () =>
    jan.heeftOntvangen({ op: '2026-10-02', bedrag: '3215.00', van: 'Werkgever B.V.' }),
  );
  await bank.als('Jan de betaalrekening opent', () => jan.opentBetaalrekening());
  await bank.dan('ziet Jan de transacties per datum, nieuw naar oud, met teken', () =>
    jan.verwachtTransactielijst([
      { datum: '2026-10-02', transacties: [{ tegenpartij: 'Werkgever B.V.', bedrag: '3215.00' }] },
      { datum: '2026-10-01', transacties: [{ tegenpartij: 'Woningstichting Thuis', bedrag: '-812.40' }] },
    ]),
  );
});

test('Rekening zonder transacties', async ({ bank }) => {
  const jan = bank.klant('Jan');
  await bank.gegeven('Jan heeft een betaalrekening met saldo € 1.000,00', () =>
    jan.heeftBetaalrekening({ saldo: '1000.00' }),
  );
  await bank.als('Jan de betaalrekening opent', () => jan.opentBetaalrekening());
  await bank.dan('ziet Jan dat er nog geen af- of bijschrijvingen zijn', () => jan.verwachtTransactielijst([]));
});

/** S2.2: per 50 transacties, met "Toon meer"; de grens van 50 valt midden in een dag. */
test('Toon meer laadt per 50 transacties', async ({ bank }) => {
  const jan = bank.klant('Jan');
  await bank.gegeven('Jan heeft een betaalrekening met saldo € 1.000,00', () =>
    jan.heeftBetaalrekening({ saldo: '1000.00' }),
  );
  await bank.en('Jan heeft 120 transacties, drie per dag', () => jan.heeftTransacties({ aantal: 120, perDag: 3 }));
  await bank.als('Jan de betaalrekening opent', () => jan.opentBetaalrekening());
  await bank.dan('ziet Jan de nieuwste 50 transacties en "Toon meer"', () =>
    jan.verwachtTransacties({ nieuwste: 50, meer: true }),
  );
  await bank.als('Jan twee keer "Toon meer" kiest', () => jan.toontMeer(2));
  await bank.dan('ziet Jan alle 120 transacties en geen "Toon meer"', () =>
    jan.verwachtTransacties({ nieuwste: 120, meer: false }),
  );
});

/** S4.1: een transactie uitklappen toont type, Van, Naar en de data (doc/werkwijze-mse.md, slice 4). */
test('Details van een incasso', async ({ bank }) => {
  const jan = bank.klant('Jan');
  await bank.gegeven('Jan heeft een betaalrekening met saldo € 1.000,00', () =>
    jan.heeftBetaalrekening({ saldo: '1000.00' }),
  );
  await bank.en('op 1 oktober 2026 om 06:02 incasseerde Woningstichting Thuis (NL91ABNA0417164300) € 812,40 bij Jan', () =>
    jan.heeftBetaald({
      op: '2026-10-01T06:02',
      bedrag: '812.40',
      aan: 'Woningstichting Thuis',
      tegenIban: 'NL91ABNA0417164300',
      type: 'INCASSO',
    }),
  );
  await bank.als('Jan deze transactie uitklapt', () => jan.klaptTransactieUit('Woningstichting Thuis'));
  await bank.dan('ziet Jan de details van de incasso', () =>
    jan.verwachtDetails({
      transactietype: 'INCASSO',
      van: { naam: 'Jan', iban: jan.iban() },
      naar: { naam: 'Woningstichting Thuis', iban: 'NL91ABNA0417164300' },
      datumTransactie: '2026-10-01T06:02',
      uitgevoerdOp: '2026-10-01',
    }),
  );
});

test('Details van een bijschrijving', async ({ bank }) => {
  const jan = bank.klant('Jan');
  await bank.gegeven('Jan heeft een betaalrekening met saldo € 1.000,00', () =>
    jan.heeftBetaalrekening({ saldo: '1000.00' }),
  );
  await bank.en('op 2 oktober 2026 om 09:15 maakte Werkgever B.V. (NL20INGB0001234567) € 3.215,00 over via Online bankieren', () =>
    jan.heeftOntvangen({
      op: '2026-10-02T09:15',
      bedrag: '3215.00',
      van: 'Werkgever B.V.',
      tegenIban: 'NL20INGB0001234567',
      type: 'ONLINE_BANKIEREN',
    }),
  );
  await bank.als('Jan deze transactie uitklapt', () => jan.klaptTransactieUit('Werkgever B.V.'));
  await bank.dan('ziet Jan Werkgever B.V. bij Van en zichzelf bij Naar', () =>
    jan.verwachtDetails({
      transactietype: 'ONLINE_BANKIEREN',
      van: { naam: 'Werkgever B.V.', iban: 'NL20INGB0001234567' },
      naar: { naam: 'Jan', iban: jan.iban() },
      datumTransactie: '2026-10-02T09:15',
      uitgevoerdOp: '2026-10-02',
    }),
  );
});

test('Betaalautomaat zonder tegen-IBAN', async ({ bank }) => {
  const jan = bank.klant('Jan');
  await bank.gegeven('Jan heeft een betaalrekening met saldo € 1.000,00', () =>
    jan.heeftBetaalrekening({ saldo: '1000.00' }),
  );
  await bank.en('op 3 oktober 2026 om 13:26 betaalde Jan € 63,48 bij Albert Heijn met de betaalautomaat', () =>
    jan.heeftBetaald({ op: '2026-10-03T13:26', bedrag: '63.48', aan: 'Albert Heijn', type: 'BETAALAUTOMAAT' }),
  );
  await bank.als('Jan deze transactie uitklapt', () => jan.klaptTransactieUit('Albert Heijn'));
  await bank.dan('ziet Jan geen IBAN bij Naar', () =>
    jan.verwachtDetails({ transactietype: 'BETAALAUTOMAAT', naar: { naam: 'Albert Heijn', iban: null } }),
  );
});
