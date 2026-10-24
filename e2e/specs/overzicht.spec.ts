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
