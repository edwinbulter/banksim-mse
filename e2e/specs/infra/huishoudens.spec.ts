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

test('Elke klant koopt elke maand bij webwinkels, van oktober 2021 t/m december 2026', { tag: '@simulatiedatum' }, async ({ omgeving }) => {
  await omgeving.gegeven('BankSim-MSE is geïnstalleerd', () => omgeving.installeer());
  await omgeving.als('de beheerder de simulatiedatum op 31 december 2026 zet', () =>
    omgeving.zetSimulatiedatum('2026-12-31'),
  );
  await omgeving.dan(
    'heeft elk van de 10 klanten van november 2021 t/m december 2026 elke maand 1 tot en met 4 betalingen aan Webwinkel Alles, BoekenBox, ModeOnline of ElektroShop, elk tussen € 10,00 en € 250,00',
    () => omgeving.verwachtWebwinkelsPerMaand('2021-11', '2026-12', { min: 1, max: 4 }, { min: '10.00', max: '250.00' }),
  );
  await omgeving.en('heeft elk van de 10 klanten in oktober 2021 hoogstens 4 van zulke betalingen, want daar vervalt een aankoop als het geld krap is', () =>
    omgeving.verwachtWebwinkelsPerMaand('2021-10', '2021-10', { min: 0, max: 4 }, { min: '10.00', max: '250.00' }),
  );
});

test('Klanten nemen contant geld op bij de geldautomaat, van oktober 2021 t/m december 2026', { tag: '@simulatiedatum' }, async ({ omgeving }) => {
  await omgeving.gegeven('BankSim-MSE is geïnstalleerd', () => omgeving.installeer());
  await omgeving.als('de beheerder de simulatiedatum op 31 december 2026 zet', () =>
    omgeving.zetSimulatiedatum('2026-12-31'),
  );
  await omgeving.dan(
    'heeft elk van de 10 klanten in elk van de 63 maanden hoogstens 2 opnames bij Geldautomaat Utrecht Centrum, Geldautomaat Amersfoort Centrum of Geldautomaat Zuidstad Centrum, elk tussen € 20,00 en € 200,00, in hele tientjes',
    () => omgeving.verwachtGeldopnamesPerMaand('2021-10', '2026-12', { min: 0, max: 2 }, { min: '20.00', max: '200.00' }),
  );
  await omgeving.en('heeft elk van de 10 klanten minstens één opname', () => omgeving.verwachtMinstensEenGeldopname());
});

test('Klanten betalen elkaar, van oktober 2021 t/m december 2026', { tag: '@simulatiedatum' }, async ({ omgeving }) => {
  await omgeving.gegeven('BankSim-MSE is geïnstalleerd', () => omgeving.installeer());
  await omgeving.als('de beheerder de simulatiedatum op 31 december 2026 zet', () =>
    omgeving.zetSimulatiedatum('2026-12-31'),
  );
  await omgeving.dan(
    'heeft elk van de 10 klanten in elk van de 63 maanden hoogstens 3 betalingen aan andere klanten, elk tussen € 5,00 en € 150,00',
    () => omgeving.verwachtBetalingenAanAndereKlanten('2021-10', '2026-12', 3, { min: '5.00', max: '150.00' }),
  );
  await omgeving.en('heeft elk van de 10 klanten minstens één betaling aan en minstens één betaling van een andere klant', () =>
    omgeving.verwachtBetalingAanEnVanAndereKlant(),
  );
  await omgeving.en('komt alles wat de 10 klanten aan elkaar betalen samen op € 0,00 uit', () =>
    omgeving.verwachtBetalingenTussenKlantenInBalans(),
  );
});

test('Elke klant betaalt elke maand zijn vaste lasten', { tag: '@simulatiedatum' }, async ({ omgeving }) => {
  await omgeving.gegeven('BankSim-MSE is geïnstalleerd', () => omgeving.installeer());
  await omgeving.als('de beheerder de simulatiedatum op 31 december 2026 zet', () =>
    omgeving.zetSimulatiedatum('2026-12-31'),
  );
  await omgeving.dan(
    'heeft elk van de 10 klanten in elk van de 63 maanden precies één betaling aan Groenstroom Energie, Waterbedrijf Midden, NetPlus Internet, Mobiel Direct, Zorgverzekeraar Gezond en Verzekeringen Zeker',
    () => omgeving.verwachtVasteLastenPerMaand('2021-10', '2026-12'),
  );
  await omgeving.en('betaalt Jan in december 2026 de bedragen uit zijn profiel', () =>
    omgeving.verwachtVasteLastenVan('Jan de Vries', '2026-12', {
      'Groenstroom Energie': '-95.00',
      'Waterbedrijf Midden': '-7.50',
      'NetPlus Internet': '-45.00',
      'Mobiel Direct': '-17.50',
      'Zorgverzekeraar Gezond': '-140.00',
      'Verzekeringen Zeker': '-18.00',
    }),
  );
});

test('Elke klant legt elke maand een vast bedrag in op zijn spaarrekening', { tag: '@simulatiedatum' }, async ({ omgeving }) => {
  await omgeving.gegeven('BankSim-MSE is geïnstalleerd', () => omgeving.installeer());
  await omgeving.als('de beheerder de simulatiedatum op 31 december 2026 zet', () =>
    omgeving.zetSimulatiedatum('2026-12-31'),
  );
  await omgeving.dan('heeft elk van de 10 klanten in elk van de 63 maanden precies één inleg op de 25e op zijn spaarrekening', () =>
    omgeving.verwachtInlegPerMaand('2021-10', '2026-12'),
  );
  await omgeving.en('is van de betaalrekening van Jan in december 2026 € 400,00 naar zijn spaarrekening gegaan', () =>
    omgeving.verwachtAfschrijvingNaarSpaarrekening('Jan de Vries', '2026-12', '-400.00'),
  );
  await omgeving.en('staat er op de spaarrekening van Jan € 41.588,06', () =>
    omgeving.verwachtSpaarsaldo('Jan de Vries', '41588.06'),
  );
});

test('Een klant met een dreigend tekort neemt eerst geld op van zijn spaarrekening', { tag: '@simulatiedatum' }, async ({ omgeving }) => {
  await omgeving.gegeven('BankSim-MSE is geïnstalleerd', () => omgeving.installeer());
  await omgeving.als('de beheerder de simulatiedatum op 31 december 2026 zet', () =>
    omgeving.zetSimulatiedatum('2026-12-31'),
  );
  await omgeving.dan('heeft Lisa op 1 oktober 2021 een opname van € 200,00 op haar spaarrekening', () =>
    omgeving.verwachtOpnames('Lisa Jansen', [{ datum: '2021-10-01', bedrag: '-200.00' }]),
  );
  await omgeving.en('heeft Mohamed op 1 en op 15 oktober 2021 een opname van € 200,00', () =>
    omgeving.verwachtOpnames('Mohamed El Amrani', [
      { datum: '2021-10-01', bedrag: '-200.00' },
      { datum: '2021-10-15', bedrag: '-200.00' },
    ]),
  );
  await omgeving.en('heeft geen van de andere 8 klanten een opname', () =>
    omgeving.verwachtGeenOpnamesBehalveBij(['Lisa Jansen', 'Mohamed El Amrani']),
  );
  await omgeving.en('staat er op de spaarrekening van Lisa € 17.185,01', () =>
    omgeving.verwachtSpaarsaldo('Lisa Jansen', '17185.01'),
  );
  await omgeving.en('staat er op de spaarrekening van Mohamed € 30.932,70', () =>
    omgeving.verwachtSpaarsaldo('Mohamed El Amrani', '30932.70'),
  );
});

test('Elke spaarrekening krijgt elke maand rente, van oktober 2021 t/m december 2026', { tag: '@simulatiedatum' }, async ({ omgeving }) => {
  await omgeving.gegeven('BankSim-MSE is geïnstalleerd', () => omgeving.installeer());
  await omgeving.dan('krijgt Jan op 31 oktober 2021 € 16,79 rente', () =>
    omgeving.verwachtRente('Jan de Vries', '2021-10-31', '16.79'),
  );
  await omgeving.als('de beheerder de simulatiedatum op 31 december 2026 zet', () =>
    omgeving.zetSimulatiedatum('2026-12-31'),
  );
  await omgeving.dan('heeft elk van de 10 spaarrekeningen in elk van de 63 maanden precies één rentebijschrijving, op de laatste dag van de maand', () =>
    omgeving.verwachtRentePerMaand('2021-10', '2026-12'),
  );
  await omgeving.en('staat er op de spaarrekening van Jan € 41.588,06', () =>
    omgeving.verwachtSpaarsaldo('Jan de Vries', '41588.06'),
  );
});

test('Werknemers krijgen elke mei vakantiegeld, van 2022 t/m 2026', { tag: '@simulatiedatum' }, async ({ omgeving }) => {
  const werknemers = [
    { naam: 'Jan de Vries', werkgever: 'Techniek Noord B.V.', salaris: '3100.00', vakantiegeld: '2852.00' },
    { naam: 'Sanne Bakker', werkgever: 'Zorggroep Rivierenland', salaris: '4800.00', vakantiegeld: '4416.00' },
    { naam: 'Mohamed El Amrani', werkgever: 'Logistiek Centraal B.V.', salaris: '4200.00', vakantiegeld: '3864.00' },
    { naam: 'Lisa Jansen', werkgever: 'Onderwijsgroep Midden', salaris: '2350.00', vakantiegeld: '2162.00' },
    { naam: 'Fatma Yilmaz', werkgever: 'Gemeente Zuidstad Salarisadministratie', salaris: '5200.00', vakantiegeld: '4784.00' },
    { naam: 'Emma de Boer', werkgever: 'Kompas Advies B.V.', salaris: '3900.00', vakantiegeld: '3588.00' },
    { naam: 'Noor Hendriks', werkgever: 'Bouwbedrijf Van Dijk B.V.', salaris: '6000.00', vakantiegeld: '5520.00' },
  ];
  await omgeving.gegeven('BankSim-MSE is geïnstalleerd', () => omgeving.installeer());
  await omgeving.als('de beheerder de simulatiedatum op 31 december 2026 zet', () =>
    omgeving.zetSimulatiedatum('2026-12-31'),
  );
  await omgeving.dan('krijgt elk van de 7 werknemers in mei van elk van de 5 jaren, naast zijn salaris, precies één keer zijn vakantiegeld van zijn werkgever', () =>
    omgeving.verwachtVakantiegeldInMei(werknemers, [2022, 2023, 2024, 2025, 2026]),
  );
  await omgeving.en('krijgen zij in de andere maanden alleen hun salaris van die werkgever', () =>
    omgeving.verwachtAlleenSalarisBuitenMei(werknemers, '2021-10', '2026-12'),
  );
});

test('Klanten krijgen elk jaar belastingteruggave, van 2022 t/m 2026', { tag: '@simulatiedatum' }, async ({ omgeving }) => {
  await omgeving.gegeven('BankSim-MSE is geïnstalleerd', () => omgeving.installeer());
  await omgeving.als('de beheerder de simulatiedatum op 31 december 2026 zet', () =>
    omgeving.zetSimulatiedatum('2026-12-31'),
  );
  await omgeving.dan(
    'krijgt elk van de 9 klanten, behalve Daan Smit, in elk van de 5 jaren precies één bijschrijving van de Belastingdienst, in april, mei of juni, tussen € 80,00 en € 1.200,00',
    () => omgeving.verwachtBelastingteruggave('Daan Smit', [2022, 2023, 2024, 2025, 2026], { min: '80.00', max: '1200.00' }),
  );
  await omgeving.en('krijgt Daan Smit nooit iets van de Belastingdienst', () =>
    omgeving.verwachtNietsVanDeBelastingdienst('Daan Smit'),
  );
});

test('Elke klant betaalt gemeentelijke belastingen in 10 termijnen per jaar', { tag: '@simulatiedatum' }, async ({ omgeving }) => {
  const alleenwonenden = ['Jan de Vries', 'Lisa Jansen', 'Daan Smit', 'Ruud Mulder'];
  await omgeving.gegeven('BankSim-MSE is geïnstalleerd', () => omgeving.installeer());
  await omgeving.als('de beheerder de simulatiedatum op 31 december 2026 zet', () =>
    omgeving.zetSimulatiedatum('2026-12-31'),
  );
  await omgeving.dan(
    'betaalt elk van de 10 klanten van oktober 2021 t/m december 2026 in elke maand van maart t/m december precies één keer aan Gemeente Zuidstad',
    () => omgeving.verwachtGemeentelijkeBelastingenPerMaand('2021-10', '2026-12'),
  );
  await omgeving.en('betaalt hij in januari en februari niets aan Gemeente Zuidstad', () =>
    omgeving.verwachtGeenGemeentelijkeBelastingenInJanuariEnFebruari('2021-10', '2026-12'),
  );
  await omgeving.en('betalen Jan, Lisa, Daan en Ruud, die alleen wonen, € 38,00 per termijn', () =>
    omgeving.verwachtTermijnGemeentelijkeBelastingen(alleenwonenden, '-38.00'),
  );
  await omgeving.en('betalen de andere 6 klanten € 55,00 per termijn', () =>
    omgeving.verwachtTermijnGemeentelijkeBelastingenVanDeAnderen(alleenwonenden, '-55.00'),
  );
});

test('Werknemers leggen elke mei extra in na hun vakantiegeld, van 2022 t/m 2026', { tag: '@simulatiedatum' }, async ({ omgeving }) => {
  const extraInleg = [
    { naam: 'Jan de Vries', bedrag: '1240.00' },
    { naam: 'Sanne Bakker', bedrag: '1920.00' },
    { naam: 'Mohamed El Amrani', bedrag: '1680.00' },
    { naam: 'Lisa Jansen', bedrag: '940.00' },
    { naam: 'Fatma Yilmaz', bedrag: '2080.00' },
    { naam: 'Emma de Boer', bedrag: '1560.00' },
    { naam: 'Noor Hendriks', bedrag: '2400.00' },
  ];
  await omgeving.gegeven('BankSim-MSE is geïnstalleerd', () => omgeving.installeer());
  await omgeving.als('de beheerder de simulatiedatum op 31 december 2026 zet', () =>
    omgeving.zetSimulatiedatum('2026-12-31'),
  );
  await omgeving.dan(
    'heeft elk van de 7 werknemers in mei van elk van de 5 jaren precies één extra inleg op de 27e op zijn spaarrekening, van 40% van zijn salaris',
    () => omgeving.verwachtExtraInlegInMei(extraInleg, [2022, 2023, 2024, 2025, 2026]),
  );
  await omgeving.en('hebben Pieter, Daan en Ruud nooit een inleg op een andere dag dan de 25e', () =>
    omgeving.verwachtAlleenInlegOpDe25e(['Pieter Visser', 'Daan Smit', 'Ruud Mulder']),
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
