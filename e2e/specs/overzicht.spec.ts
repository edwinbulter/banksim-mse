import { test } from '../dsl/fixtures';

test('Klant ziet de betaalrekening met saldo in het overzicht', { tag: '@rook' }, async ({ bank }) => {
  const jan = bank.klant('Jan');
  await bank.gegeven('Jan heeft een betaalrekening met saldo € 1.842,17', () =>
    jan.heeftBetaalrekening({ saldo: '1842.17' }),
  );
  await bank.als('Jan het overzicht opent', () => jan.opentOverzicht());
  await bank.dan('ziet Jan de betaalrekening met saldo € 1.842,17', () =>
    jan.verwachtRekeningInOverzicht('betaal', '1842.17'),
  );
});

// S8.2: de spaarrekening staat in het overzicht naast de betaalrekening. Vandaag geopend, dus nog zonder rente.
test('Klant ziet ook de spaarrekening met saldo in het overzicht', async ({ bank }) => {
  const jan = bank.klant('Jan');
  await bank.gegeven('Jan heeft een betaalrekening met saldo € 1.842,17', () =>
    jan.heeftBetaalrekening({ saldo: '1842.17' }),
  );
  await bank.en('Jan heeft een spaarrekening met saldo € 12.405,63', () =>
    jan.heeftSpaarrekening({ saldo: '12405.63' }),
  );
  await bank.als('Jan het overzicht opent', () => jan.opentOverzicht());
  await bank.dan('ziet Jan de spaarrekening met saldo € 12.405,63', () =>
    jan.verwachtRekeningInOverzicht('spaar', '12405.63'),
  );
  await bank.en('ziet Jan de betaalrekening met saldo € 1.842,17', () =>
    jan.verwachtRekeningInOverzicht('betaal', '1842.17'),
  );
});
