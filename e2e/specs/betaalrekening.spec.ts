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
