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

/** S10.1, ADR 0022: beheer is alleen voor de rol beheerder; een klant komt op zijn eigen overzicht. */
test('Klant kan niet bij beheer', async ({ bank }) => {
  const jan = bank.klant('Jan');
  await bank.gegeven('Jan heeft een betaalrekening', () => jan.heeftBetaalrekening({ saldo: '100.00' }));
  await bank.als('Jan het beheerscherm probeert te openen', () => jan.opentBeheer());
  await bank.dan('komt Jan op zijn eigen overzicht, en ziet hij geen rekeninghouders', () =>
    jan.verwachtGeenToegangTotBeheer(),
  );
});

/** S10.1, ADR 0037: na het inloggen kiest de app het scherm bij de rol. @ui: het gaat om waar de beheerder terechtkomt. */
test('De beheerder komt na het inloggen op het beheerscherm', { tag: '@ui' }, async ({ bank }) => {
  const beheerder = bank.beheerder();
  await bank.als('de beheerder inlogt', () => beheerder.logtIn());
  await bank.dan('ziet hij het beheerscherm', () => beheerder.verwachtBeheerscherm());
});
