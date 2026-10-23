import { test } from '../dsl/fixtures';

/**
 * S3.1: de bank op een gekozen datum (doc/werkwijze-mse.md, slice 3). De simulatiedatum geldt voor de hele bank; deze
 * scenario's draaien daarom na alle andere, één voor één, en de fixture wist de datum na elk scenario.
 */
test.describe('Simulatiedatum', { tag: '@simulatiedatum' }, () => {
  test('Transacties na de simulatiedatum zijn onzichtbaar', async ({ bank }) => {
    const jan = bank.klant('Jan');
    const beheerder = bank.beheerder();
    await bank.gegeven('Jan heeft een betaalrekening met saldo € 100,00', () =>
      jan.heeftBetaalrekening({ saldo: '100.00' }),
    );
    await bank.en('op 1 oktober 2026 ontving Jan € 50,00 van Werkgever B.V.', () =>
      jan.heeftOntvangen({ op: '2026-10-01', bedrag: '50.00', van: 'Werkgever B.V.' }),
    );
    await bank.en('op 10 oktober 2026 ontving Jan € 25,00 van Werkgever B.V.', () =>
      jan.heeftOntvangen({ op: '2026-10-10', bedrag: '25.00', van: 'Werkgever B.V.' }),
    );
    await bank.en('de beheerder zet de simulatiedatum op 5 oktober 2026', () =>
      beheerder.zetSimulatiedatum('2026-10-05'),
    );
    await bank.als('Jan het overzicht opent', () => jan.opentOverzicht());
    await bank.dan('ziet Jan de betaalrekening met saldo € 150,00', () =>
      jan.verwachtRekeningInOverzicht('betaal', '150.00'),
    );
    await bank.als('Jan de betaalrekening opent', () => jan.opentBetaalrekening());
    await bank.dan('ziet Jan alleen de transactie van 1 oktober 2026', () =>
      jan.verwachtTransactielijst([
        { datum: '2026-10-01', transacties: [{ tegenpartij: 'Werkgever B.V.', bedrag: '50.00' }] },
      ]),
    );
  });

  test('Vooruitzetten maakt transacties weer zichtbaar', async ({ bank }) => {
    const jan = bank.klant('Jan');
    const beheerder = bank.beheerder();
    await bank.gegeven('Jan heeft een betaalrekening met saldo € 100,00', () =>
      jan.heeftBetaalrekening({ saldo: '100.00' }),
    );
    await bank.en('op 1 oktober 2026 ontving Jan € 50,00 van Werkgever B.V.', () =>
      jan.heeftOntvangen({ op: '2026-10-01', bedrag: '50.00', van: 'Werkgever B.V.' }),
    );
    await bank.en('op 10 oktober 2026 ontving Jan € 25,00 van Werkgever B.V.', () =>
      jan.heeftOntvangen({ op: '2026-10-10', bedrag: '25.00', van: 'Werkgever B.V.' }),
    );
    await bank.en('de beheerder heeft de simulatiedatum op 5 oktober 2026 gezet', () =>
      beheerder.zetSimulatiedatum('2026-10-05'),
    );
    await bank.als('de beheerder de simulatiedatum op 15 oktober 2026 zet', () =>
      beheerder.zetSimulatiedatum('2026-10-15'),
    );
    await bank.en('Jan het overzicht opent', () => jan.opentOverzicht());
    await bank.dan('ziet Jan de betaalrekening met saldo € 175,00', () =>
      jan.verwachtRekeningInOverzicht('betaal', '175.00'),
    );
    await bank.als('Jan de betaalrekening opent', () => jan.opentBetaalrekening());
    await bank.dan('ziet Jan de transacties van 10 en 1 oktober 2026', () =>
      jan.verwachtTransactielijst([
        { datum: '2026-10-10', transacties: [{ tegenpartij: 'Werkgever B.V.', bedrag: '25.00' }] },
        { datum: '2026-10-01', transacties: [{ tegenpartij: 'Werkgever B.V.', bedrag: '50.00' }] },
      ]),
    );
  });

  test('Een klant kan de simulatiedatum niet zetten', async ({ bank }) => {
    const jan = bank.klant('Jan');
    await bank.gegeven('Jan heeft een betaalrekening met saldo € 100,00', () =>
      jan.heeftBetaalrekening({ saldo: '100.00' }),
    );
    await bank.als('Jan de simulatiedatum op 5 oktober 2026 probeert te zetten', () =>
      jan.probeertSimulatiedatumTeZetten('2026-10-05'),
    );
    await bank.dan('wordt dat geweigerd', () => jan.verwachtGeweigerd());
  });
});
