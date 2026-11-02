import { test } from '../dsl/fixtures';

/**
 * S10.1 (FO "Admin scherm"): de beheerder ziet de rekeninghouders met hun rekeningen en saldi. Zoeken gaat op naam
 * of rekeningnummer: de testdata heeft veel klanten met dezelfde naam, het rekeningnummer is uniek.
 */
test('De beheerder vindt een klant met de saldi van zijn rekeningen', async ({ bank }) => {
  const jan = bank.klant('Jan');
  const beheerder = bank.beheerder();
  await bank.gegeven('Jan heeft een betaalrekening met saldo € 1.842,17 en een spaarrekening met saldo € 12.405,63', async () => {
    await jan.heeftBetaalrekening({ saldo: '1842.17' });
    await jan.heeftSpaarrekening({ saldo: '12405.63' });
  });
  await bank.als('de beheerder op het beheerscherm zoekt op het rekeningnummer van Jan', () =>
    beheerder.zoektRekeninghouder(jan),
  );
  await bank.dan('ziet de beheerder Jan met zijn betaalrekening, € 1.842,17, zijn spaarrekening en € 12.405,63', () =>
    beheerder.verwachtRekeninghouder(jan, { saldo: '1842.17', spaarsaldo: '12405.63' }),
  );
});
