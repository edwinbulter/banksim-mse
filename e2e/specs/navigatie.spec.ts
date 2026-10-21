import { test } from '../dsl/fixtures';

/**
 * S2a.1: terug van de betaalrekening naar het overzicht, ook met de terugknop van de browser; herladen houdt de
 * betaalrekening open (doc/werkwijze-mse.md, slice 2a). Alleen in de browser: de API verandert niet.
 */
test.describe('Navigatie', { tag: '@ui' }, () => {
  test.beforeEach(async ({ bank }) => {
    const jan = bank.klant('Jan');
    await bank.gegeven('Jan heeft een betaalrekening met saldo € 1.000,00', () =>
      jan.heeftBetaalrekening({ saldo: '1000.00' }),
    );
    await bank.en('op 1 oktober 2026 betaalde Jan € 812,40 aan Woningstichting Thuis', () =>
      jan.heeftBetaald({ op: '2026-10-01', bedrag: '812.40', aan: 'Woningstichting Thuis' }),
    );
    await bank.en('Jan heeft de betaalrekening geopend', () => jan.opentBetaalrekening());
  });

  test('Terug naar het overzicht', async ({ bank }) => {
    const jan = bank.klant('Jan');
    await bank.als('Jan "Overzicht" kiest', () => jan.gaatNaarOverzicht());
    await bank.dan('ziet Jan de betaalrekening met saldo € 187,60', () =>
      jan.verwachtRekeningInOverzicht('betaal', '187.60'),
    );
  });

  test('Terug met de terugknop van de browser', async ({ bank }) => {
    const jan = bank.klant('Jan');
    await bank.als('Jan de terugknop van de browser gebruikt', () => jan.gaatTerugInBrowser());
    await bank.dan('ziet Jan de betaalrekening met saldo € 187,60, zonder dat de app opnieuw laadt', async () => {
      await jan.verwachtRekeningInOverzicht('betaal', '187.60');
      await jan.verwachtDatDeAppNietOpnieuwLaadde();
    });
  });

  test('Herladen op de betaalrekening', async ({ bank }) => {
    const jan = bank.klant('Jan');
    await bank.als('Jan de pagina herlaadt', () => jan.herlaadt());
    await bank.dan('ziet Jan nog steeds de transacties van de betaalrekening', () =>
      jan.verwachtTransactielijst([
        { datum: '2026-10-01', transacties: [{ tegenpartij: 'Woningstichting Thuis', bedrag: '-812.40' }] },
      ]),
    );
  });
});
