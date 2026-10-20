import { test } from '../../dsl/fixtures';

/**
 * Een upgrade vervangt de applicatie, niet de data (doc/werkwijze-mse.md, hoofdstuk 9.2 en 9.4; ADR 0009).
 * @destructief: de upgrade vervangt de draaiende installatie door een andere versie; alleen in het CI-cluster.
 */
test('Een upgrade behoudt de data', { tag: '@destructief' }, async ({ omgeving, bank }) => {
  const jan = bank.klant('Jan');
  await omgeving.gegeven('Jan heeft een betaalrekening met saldo € 100,00', () =>
    jan.heeftBetaalrekening({ saldo: '100.00' }),
  );
  await omgeving.als('de beheerder een nieuwe versie van BankSim-MSE installeert', () =>
    omgeving.installeerNieuweVersie(),
  );
  await omgeving.dan('antwoordt BankSim-MSE met de nieuwe versie', () => omgeving.verwachtBereikbaar());
  await omgeving.en('ziet Jan na het inloggen nog de betaalrekening met saldo € 100,00', async () => {
    await jan.opentOverzicht();
    await jan.verwachtRekeningInOverzicht('betaal', '100.00');
  });
});
