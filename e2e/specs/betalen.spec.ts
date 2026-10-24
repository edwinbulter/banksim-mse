import { test } from '../dsl/fixtures';

// S7.1: geld overmaken naar een contact. De contactenlijst geldt voor de hele bank (FO "Betalen scherm").
test('Betaling naar een contact', async ({ bank }) => {
  const jan = bank.klant('Jan');
  const sanne = bank.klant('Sanne');
  await bank.gegeven('Jan heeft een betaalrekening met saldo € 500,00', () =>
    jan.heeftBetaalrekening({ saldo: '500.00' }),
  );
  await bank.en('Sanne is een contact met een betaalrekening', () =>
    sanne.heeftBetaalrekening({ contact: true }),
  );
  await bank.als('Jan € 25,00 aan Sanne betaalt en bevestigt', () =>
    jan.betaalt({ bedrag: '25.00', aan: sanne }),
  );
  await bank.dan('staat de betaling bovenaan de transacties van Jan als -25,00 aan Sanne', () =>
    jan.verwachtBovensteTransactie('Sanne', '-25.00'),
  );
  await bank.en('is het saldo van Jan € 475,00', () => jan.verwachtSaldo('475.00'));
  await bank.en('ziet Sanne bovenaan een bijschrijving van +25,00 van Jan', () =>
    sanne.verwachtBovensteTransactie('Jan', '25.00'),
  );
});
