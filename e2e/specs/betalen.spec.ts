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

test('Betaling met een naam die niet bij het rekeningnummer past wordt geweigerd', async ({ bank }) => {
  const jan = bank.klant('Jan');
  const sanne = bank.klant('Sanne');
  await bank.en('Sanne is een contact met een betaalrekening', () =>
    sanne.heeftBetaalrekening({ contact: true }),
  );
  await bank.als('Jan € 25,00 probeert te betalen aan de betaalrekening van Sanne, met de naam "Piet"', () =>
    jan.probeertTeBetalen({ bedrag: '25.00', aan: sanne, naam: 'Piet' }),
  );
  await bank.dan('wordt de betaling geweigerd', () => jan.verwachtWeigering('naam-past-niet'));
  await bank.en('is het saldo van Jan € 500,00', () => jan.verwachtSaldo('500.00'));
  await bank.en('heeft Sanne geen bijschrijving ontvangen', () => sanne.verwachtGeenTransacties());
});

test('Rood staan is niet toegestaan', async ({ bank }) => {
  const jan = bank.klant('Jan');
  const sanne = bank.klant('Sanne');
  await bank.en('Sanne is een contact met een betaalrekening', () =>
    sanne.heeftBetaalrekening({ contact: true }),
  );
  await bank.als('Jan € 500,01 probeert te betalen aan Sanne', () =>
    jan.probeertTeBetalen({ bedrag: '500.01', aan: sanne }),
  );
  await bank.dan('krijgt Jan de melding "Saldo ontoereikend"', () => jan.verwachtWeigering('saldo-ontoereikend'));
});

// CFR in deze slice: een herhaald verzoek (dubbele klik, of opnieuw na een storing) boekt niet nog een keer.
test('Dubbel verzonden betaling wordt één keer geboekt', async ({ bank }) => {
  const jan = bank.klant('Jan');
  const sanne = bank.klant('Sanne');
  await bank.en('Sanne is een contact met een betaalrekening', () =>
    sanne.heeftBetaalrekening({ contact: true }),
  );
  await bank.als('Jan € 25,00 aan Sanne betaalt en de bevestiging twee keer verstuurt', () =>
    jan.betaaltDubbel({ bedrag: '25.00', aan: sanne }),
  );
  await bank.dan('is het saldo van Jan € 475,00', () => jan.verwachtSaldo('475.00'));
  await bank.en('ziet Sanne precies één bijschrijving van +25,00 van Jan', () =>
    sanne.verwachtAlleenTransactie('Jan', '25.00'),
  );
});

// De simulatiedatum geldt voor de hele bank: dit scenario draait na de andere, één voor één (ADR 0014).
test('Een betaling mag een latere boeking niet rood laten staan', { tag: '@simulatiedatum' }, async ({ bank }) => {
  const jan = bank.klant('Jan');
  const sanne = bank.klant('Sanne');
  await bank.en('Sanne is een contact met een betaalrekening', () =>
    sanne.heeftBetaalrekening({ contact: true }),
  );
  await bank.en('op 10 oktober 2026 wordt € 450,00 van Jan geïncasseerd door Woningstichting Thuis', () =>
    jan.heeftBetaald({ op: '2026-10-10', bedrag: '450.00', aan: 'Woningstichting Thuis', type: 'INCASSO' }),
  );
  await bank.en('de beheerder zet de simulatiedatum op 5 oktober 2026', () =>
    bank.beheerder().zetSimulatiedatum('2026-10-05'),
  );
  await bank.als('Jan op 5 oktober € 100,00 probeert te betalen aan Sanne', () =>
    jan.probeertTeBetalen({ bedrag: '100.00', aan: sanne }),
  );
  await bank.dan('krijgt Jan de melding "Saldo ontoereikend"', () => jan.verwachtWeigering('saldo-ontoereikend'));
});
