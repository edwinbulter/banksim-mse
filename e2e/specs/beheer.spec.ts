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

/** S10.1 (FO "Admin scherm"): de beheerder ziet de transacties van een klant, maar kan niets veranderen. */
test('De beheerder bekijkt de betaalrekening van een klant alleen-lezen', async ({ bank }) => {
  const beheerder = bank.beheerder();
  const jan = bank.klant('Jan');
  await bank.gegeven('Jan heeft een betaalrekening', () => jan.heeftBetaalrekening({ saldo: '1000.00' }));
  await bank.en('op 1 oktober 2026 betaalde Jan € 812,40 aan Woningstichting Thuis', () =>
    jan.heeftBetaald({ op: '2026-10-01', bedrag: '812.40', aan: 'Woningstichting Thuis' }),
  );
  await bank.en('op 2 oktober 2026 ontving Jan € 3.215,00 van Werkgever B.V.', () =>
    jan.heeftOntvangen({ op: '2026-10-02', bedrag: '3215.00', van: 'Werkgever B.V.' }),
  );
  await bank.als('de beheerder de betaalrekening van Jan opent', () => beheerder.opentBetaalrekeningVan(jan));
  await bank.dan('ziet de beheerder de transacties van Jan per datum, nieuw naar oud', () =>
    beheerder.verwachtTransactielijst([
      { datum: '2026-10-02', transacties: [{ tegenpartij: 'Werkgever B.V.', bedrag: '3215.00' }] },
      { datum: '2026-10-01', transacties: [{ tegenpartij: 'Woningstichting Thuis', bedrag: '-812.40' }] },
    ]),
  );
  await bank.maar('kan de beheerder niet betalen', () => beheerder.verwachtAlleenLezen());
});

/** S10.1, ADR 0038: ook de spaarrekening alleen-lezen; de mutaties zoals Jan ze ziet, zonder Inleggen en Opnemen. */
test('De beheerder bekijkt de spaarrekening van een klant alleen-lezen', async ({ bank }) => {
  const beheerder = bank.beheerder();
  const jan = bank.klant('Jan');
  await bank.gegeven('Jan heeft € 1.000,00 op de betaalrekening en € 5.000,00 op de spaarrekening', async () => {
    await jan.heeftBetaalrekening({ saldo: '1000.00' });
    await jan.heeftSpaarrekening({ saldo: '5000.00' });
  });
  await bank.en('Jan heeft vandaag € 250,00 ingelegd', () => jan.legtIn('250.00'));
  await bank.als('de beheerder de spaarrekening van Jan opent', () => beheerder.opentSpaarrekeningVan(jan));
  await bank.dan('ziet de beheerder vandaag een mutatie "Inleg" van +250,00', () =>
    beheerder.verwachtSpaarmutatie({ op: 'vandaag', type: 'Inleg', bedrag: '250.00' }),
  );
  await bank.maar('kan de beheerder niet inleggen of opnemen', () => beheerder.verwachtSpaarrekeningAlleenLezen(jan));
});
