import { test } from '../../dsl/fixtures';

/** S11.1: een nieuwe installatie bevat de demo-data, zodat BankSim-MSE zonder voorbereiding te demonstreren is. */
test('Na installatie kunnen 10 klanten en een beheerder inloggen', async ({ omgeving }) => {
  await omgeving.als('de beheerder BankSim-MSE installeert', () => omgeving.installeer());
  await omgeving.dan('kan elk van de 10 klanten inloggen en ziet een betaalrekening en een spaarrekening', () =>
    omgeving.verwachtKlanten(10),
  );
  await omgeving.en('komt de beheerder na inloggen op het beheerscherm', () => omgeving.verwachtBeheerder());
});

test('Elke klant heeft elke maand transacties, van oktober 2021 t/m december 2026', { tag: '@simulatiedatum' }, async ({ omgeving }) => {
  await omgeving.gegeven('BankSim-MSE is geïnstalleerd', () => omgeving.installeer());
  await omgeving.als('de beheerder de simulatiedatum op 31 december 2026 zet', () =>
    omgeving.zetSimulatiedatum('2026-12-31'),
  );
  await omgeving.dan('heeft de betaalrekening van elk van de 10 klanten in elk van de 63 maanden transacties', () =>
    omgeving.verwachtTransactiesPerMaand('2021-10', '2026-12'),
  );
});

test('Geen betaalrekening van een demo-klant staat op enige dag rood', { tag: '@simulatiedatum' }, async ({ omgeving }) => {
  await omgeving.gegeven('BankSim-MSE is geïnstalleerd', () => omgeving.installeer());
  await omgeving.als('de beheerder de simulatiedatum op 31 december 2026 zet', () =>
    omgeving.zetSimulatiedatum('2026-12-31'),
  );
  await omgeving.dan('is het laagste lopende saldo van elk van de 10 klanten minstens € 0,00', () =>
    omgeving.verwachtNooitRood(),
  );
});

test('Elke klant doet elke maand boodschappen, van oktober 2021 t/m december 2026', { tag: '@simulatiedatum' }, async ({ omgeving }) => {
  await omgeving.gegeven('BankSim-MSE is geïnstalleerd', () => omgeving.installeer());
  await omgeving.als('de beheerder de simulatiedatum op 31 december 2026 zet', () =>
    omgeving.zetSimulatiedatum('2026-12-31'),
  );
  await omgeving.dan(
    'heeft elk van de 10 klanten in elk van de 63 maanden 1 tot en met 12 betalingen aan BuurtSuper, Prijsvast Supermarkt of Versmarkt, elk tussen € 8,00 en € 120,00',
    () => omgeving.verwachtBoodschappenPerMaand('2021-10', '2026-12', { min: 1, max: 12 }, { min: '8.00', max: '120.00' }),
  );
});

/** @destructief: de upgrade vervangt de draaiende installatie door een andere versie; alleen in het CI-cluster. */
test('Een upgrade laat de demo-data ongemoeid', { tag: '@destructief' }, async ({ omgeving }) => {
  await omgeving.gegeven('BankSim-MSE is geïnstalleerd', () => omgeving.installeer());
  const voor = await omgeving.demoData();
  await omgeving.als('de beheerder een nieuwe versie van BankSim-MSE installeert', () =>
    omgeving.installeerNieuweVersie(),
  );
  await omgeving.dan('antwoordt BankSim-MSE met de nieuwe versie', () => omgeving.verwachtBereikbaar());
  await omgeving.en('ziet elk van de 10 klanten dezelfde rekeningen met hetzelfde saldo, en dezelfde transacties op de betaalrekening', () =>
    omgeving.verwachtDemoDataGelijkAan(voor),
  );
});
