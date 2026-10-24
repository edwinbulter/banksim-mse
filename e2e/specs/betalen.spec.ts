import { test } from '../dsl/fixtures';

// S7.1: geld overmaken naar een contact. De contactenlijst geldt voor de hele bank (FO "Betalen scherm").
test.beforeEach(async ({ bank }) => {
  await bank.gegeven('Jan heeft een betaalrekening met saldo € 500,00', () =>
    bank.klant('Jan').heeftBetaalrekening({ saldo: '500.00' }),
  );
});

test('Betaling naar een contact', async ({ bank }) => {
  const jan = bank.klant('Jan');
  const sanne = bank.klant('Sanne');
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

test('Betaling naar een rekening die geen contact is wordt geweigerd', async ({ bank }) => {
  const jan = bank.klant('Jan');
  const piet = bank.klant('Piet');
  await bank.en('Piet heeft een betaalrekening, maar is geen contact', () =>
    piet.heeftBetaalrekening({ contact: false }),
  );
  await bank.als('Jan € 25,00 probeert te betalen aan de betaalrekening van Piet', () =>
    jan.probeertTeBetalen({ bedrag: '25.00', aan: piet }),
  );
  await bank.dan('wordt de betaling geweigerd', () => jan.verwachtWeigering('onbekend-contact'));
  await bank.en('is het saldo van Jan € 500,00', () => jan.verwachtSaldo('500.00'));
  await bank.en('heeft Piet geen bijschrijving ontvangen', () => piet.verwachtGeenTransacties());
});
