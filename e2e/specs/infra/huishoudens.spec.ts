import { test } from '../../dsl/fixtures';

/** S11.1: een nieuwe installatie bevat de demo-data, zodat BankSim-MSE zonder voorbereiding te demonstreren is. */

/** Origineel: de lonen en uitkeringen per jaar ten opzichte van 2021. */
const LOONSTIJGING = { 2021: '1.00', 2022: '1.04', 2023: '1.12', 2024: '1.18', 2025: '1.22', 2026: '1.26' };

/** Origineel: het prijsniveau per jaar ten opzichte van 2021. */
const INFLATIE = { 2021: '1.00', 2022: '1.10', 2023: '1.16', 2024: '1.20', 2025: '1.24', 2026: '1.28' };

/** Origineel: de energieprijs per jaar ten opzichte van 2021, met de piek van 2022. */
const ENERGIEPRIJS = { 2021: '1.00', 2022: '1.80', 2023: '1.50', 2024: '1.25', 2025: '1.20', 2026: '1.20' };

/** Het maandsalaris van 2021 van de 7 werknemers. */
const WERKNEMERS = [
  { naam: 'Jan de Vries', werkgever: 'Techniek Noord B.V.', salaris: '3100.00' },
  { naam: 'Sanne Bakker', werkgever: 'Zorggroep Rivierenland', salaris: '4800.00' },
  { naam: 'Mohamed El Amrani', werkgever: 'Logistiek Centraal B.V.', salaris: '4200.00' },
  { naam: 'Lisa Jansen', werkgever: 'Onderwijsgroep Midden', salaris: '2350.00' },
  { naam: 'Fatma Yilmaz', werkgever: 'Gemeente Zuidstad Salarisadministratie', salaris: '5200.00' },
  { naam: 'Emma de Boer', werkgever: 'Kompas Advies B.V.', salaris: '3900.00' },
  { naam: 'Noor Hendriks', werkgever: 'Bouwbedrijf Van Dijk B.V.', salaris: '6000.00' },
];
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
  await omgeving.en('betaalt Jan in december 2026 de bedragen uit zijn profiel, maal de inflatie van 2026, en de energie maal de energieprijs van 2026', () =>
    omgeving.verwachtVasteLastenVan('Jan de Vries', '2026-12', {
      'Groenstroom Energie': '-114.00',
      'Waterbedrijf Midden': '-9.60',
      'NetPlus Internet': '-57.60',
      'Mobiel Direct': '-22.40',
      'Zorgverzekeraar Gezond': '-179.20',
      'Verzekeringen Zeker': '-23.04',
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
  await omgeving.en('staat er op de spaarrekening van Jan € 42.666,45', () =>
    omgeving.verwachtSpaarsaldo('Jan de Vries', '42666.45'),
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
  await omgeving.en('staat er op de spaarrekening van Lisa € 18.002,50', () =>
    omgeving.verwachtSpaarsaldo('Lisa Jansen', '18002.50'),
  );
  await omgeving.en('staat er op de spaarrekening van Mohamed € 32.393,72', () =>
    omgeving.verwachtSpaarsaldo('Mohamed El Amrani', '32393.72'),
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
  await omgeving.en('staat er op de spaarrekening van Jan € 42.666,45', () =>
    omgeving.verwachtSpaarsaldo('Jan de Vries', '42666.45'),
  );
});

test('Werknemers krijgen elke mei vakantiegeld, van 2022 t/m 2026', { tag: '@simulatiedatum' }, async ({ omgeving }) => {
  await omgeving.gegeven('BankSim-MSE is geïnstalleerd', () => omgeving.installeer());
  await omgeving.als('de beheerder de simulatiedatum op 31 december 2026 zet', () =>
    omgeving.zetSimulatiedatum('2026-12-31'),
  );
  await omgeving.dan(
    'krijgt elk van de 7 werknemers in mei van elk van de 5 jaren, naast zijn salaris van dat jaar, precies één keer 92% daarvan als vakantiegeld van zijn werkgever',
    () => omgeving.verwachtVakantiegeldInMei(WERKNEMERS, LOONSTIJGING, [2022, 2023, 2024, 2025, 2026]),
  );
  await omgeving.en('krijgt Jan in mei 2022 van Techniek Noord B.V. zijn salaris van € 3.224,00 en € 2.966,08 vakantiegeld', () =>
    omgeving.verwachtBetalingenVan('Jan de Vries', 'Techniek Noord B.V.', '2022-05', ['3224.00', '2966.08']),
  );
  await omgeving.en('krijgen zij in de andere maanden alleen hun salaris van dat jaar van die werkgever', () =>
    omgeving.verwachtAlleenSalarisBuitenMei(WERKNEMERS, LOONSTIJGING, '2021-10', '2026-12'),
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
  await omgeving.en(
    'betalen Jan, Lisa, Daan en Ruud, die alleen wonen, in 2021 € 38,00 per termijn, daarna elk jaar maal de inflatie: € 41,80 · € 44,08 · € 45,60 · € 47,12 · € 48,64',
    () => omgeving.verwachtTermijnGemeentelijkeBelastingen(alleenwonenden, '38.00', INFLATIE),
  );
  await omgeving.en(
    'betalen de andere 6 klanten in 2021 € 55,00 per termijn, daarna elk jaar maal de inflatie: € 60,50 · € 63,80 · € 66,00 · € 68,20 · € 70,40',
    () => omgeving.verwachtTermijnGemeentelijkeBelastingenVanDeAnderen(alleenwonenden, '55.00', INFLATIE),
  );
});

test('Werknemers leggen elke mei extra in na hun vakantiegeld, van 2022 t/m 2026', { tag: '@simulatiedatum' }, async ({ omgeving }) => {
  await omgeving.gegeven('BankSim-MSE is geïnstalleerd', () => omgeving.installeer());
  await omgeving.als('de beheerder de simulatiedatum op 31 december 2026 zet', () =>
    omgeving.zetSimulatiedatum('2026-12-31'),
  );
  await omgeving.dan(
    'heeft elk van de 7 werknemers in mei van elk van de 5 jaren precies één extra inleg op de 27e op zijn spaarrekening, van 40% van zijn salaris van dat jaar',
    () => omgeving.verwachtExtraInlegInMei(WERKNEMERS, LOONSTIJGING, [2022, 2023, 2024, 2025, 2026]),
  );
  await omgeving.en('legt Jan in 2022 € 1.289,60 extra in en in 2026 € 1.562,40', () =>
    omgeving.verwachtExtraInleg('Jan de Vries', [
      { datum: '2022-05-27', bedrag: '1289.60' },
      { datum: '2026-05-27', bedrag: '1562.40' },
    ]),
  );
  await omgeving.en('hebben Pieter, Daan en Ruud nooit een inleg op een andere dag dan de 25e', () =>
    omgeving.verwachtAlleenInlegOpDe25e(['Pieter Visser', 'Daan Smit', 'Ruud Mulder']),
  );
});

test('Het inkomen van elke klant stijgt elk jaar met de lonen, van 2021 t/m 2026', { tag: '@simulatiedatum' }, async ({ omgeving }) => {
  const inkomens = [
    ...WERKNEMERS.map(({ naam, werkgever, salaris }) => ({ naam, van: werkgever, bedrag: salaris })),
    { naam: 'Pieter Visser', van: 'Pensioenfonds Midden', bedrag: '2450.00' },
    { naam: 'Daan Smit', van: 'Kompas Advies B.V.', bedrag: '4650.00' },
    { naam: 'Ruud Mulder', van: 'Pensioenfonds Midden', bedrag: '2600.00' },
  ];
  await omgeving.gegeven('BankSim-MSE is geïnstalleerd', () => omgeving.installeer());
  await omgeving.als('de beheerder de simulatiedatum op 31 december 2026 zet', () =>
    omgeving.zetSimulatiedatum('2026-12-31'),
  );
  await omgeving.dan(
    'krijgt elk van de 10 klanten in elke maand zijn inkomen van 2021 maal de loonstijging van dat jaar: 1,00 · 1,04 · 1,12 · 1,18 · 1,22 · 1,26',
    () => omgeving.verwachtInkomenMetLoonstijging(inkomens, LOONSTIJGING, '2021-10', '2026-12'),
  );
  await omgeving.en(
    'krijgt Jan van Techniek Noord B.V. € 3.100,00 in 2021, € 3.224,00 in 2022, € 3.472,00 in 2023, € 3.658,00 in 2024, € 3.782,00 in 2025 en € 3.906,00 in 2026',
    () => omgeving.verwachtInkomenPerJaar('Jan de Vries', 'Techniek Noord B.V.',
      { 2021: '3100.00', 2022: '3224.00', 2023: '3472.00', 2024: '3658.00', 2025: '3782.00', 2026: '3906.00' }, '2021-10', '2026-12'),
  );
});

test('De huur en de vaste lasten stijgen elk jaar met de inflatie, van 2021 t/m 2026', { tag: '@simulatiedatum' }, async ({ omgeving }) => {
  const huurders = [
    { naam: 'Jan de Vries', aan: 'Woonstichting Thuis' },
    { naam: 'Mohamed El Amrani', aan: 'Woningcorporatie Samen' },
    { naam: 'Lisa Jansen', aan: 'Woonstichting Thuis' },
    { naam: 'Emma de Boer', aan: 'Woningcorporatie Samen' },
    { naam: 'Ruud Mulder', aan: 'Woonstichting Thuis' },
  ];
  await omgeving.gegeven('BankSim-MSE is geïnstalleerd', () => omgeving.installeer());
  await omgeving.als('de beheerder de simulatiedatum op 31 december 2026 zet', () =>
    omgeving.zetSimulatiedatum('2026-12-31'),
  );
  await omgeving.dan(
    'betaalt elk van de 10 klanten in elke maand zijn huur, zorgverzekering, verzekering, internet, water en mobiel van 2021 maal de inflatie van dat jaar: 1,00 · 1,10 · 1,16 · 1,20 · 1,24 · 1,28',
    () => omgeving.verwachtVasteLastenMetInflatie(huurders, INFLATIE, '2021-10', '2026-12'),
  );
  await omgeving.en('betaalt hij zijn gemeentelijke belastingen per termijn ook maal de inflatie', () =>
    omgeving.verwachtGemeentelijkeBelastingenMetInflatie(INFLATIE, '2021-10'),
  );
  await omgeving.en('blijft de hypotheek van Sanne, Fatma, Daan en Noor elk jaar gelijk', () =>
    omgeving.verwachtElkeMaandHetzelfdeBedragAan(['Sanne Bakker', 'Fatma Yilmaz', 'Daan Smit', 'Noor Hendriks'], 'Hypotheekbank Hollandia', '2021-10', '2026-12'),
  );
  await omgeving.en(
    'betaalt Jan aan Woonstichting Thuis € 950,00 in 2021, € 1.045,00 in 2022, € 1.102,00 in 2023, € 1.140,00 in 2024, € 1.178,00 in 2025 en € 1.216,00 in 2026',
    () => omgeving.verwachtWoonlastenPerJaar('Jan de Vries', 'Woonstichting Thuis',
      { 2021: '-950.00', 2022: '-1045.00', 2023: '-1102.00', 2024: '-1140.00', 2025: '-1178.00', 2026: '-1216.00' }, '2021-10', '2026-12'),
  );
});

test('De energie volgt elk jaar de energieprijzen, van 2021 t/m 2026', { tag: '@simulatiedatum' }, async ({ omgeving }) => {
  await omgeving.gegeven('BankSim-MSE is geïnstalleerd', () => omgeving.installeer());
  await omgeving.als('de beheerder de simulatiedatum op 31 december 2026 zet', () =>
    omgeving.zetSimulatiedatum('2026-12-31'),
  );
  await omgeving.dan(
    'betaalt elk van de 10 klanten in elke maand aan Groenstroom Energie zijn bedrag van 2021 maal de energieprijs van dat jaar: 1,00 · 1,80 · 1,50 · 1,25 · 1,20 · 1,20',
    () => omgeving.verwachtEnergieMetEnergieprijs(ENERGIEPRIJS, '2021-10', '2026-12'),
  );
  await omgeving.en(
    'betaalt Jan aan Groenstroom Energie € 95,00 in 2021, € 171,00 in 2022, € 142,50 in 2023, € 118,75 in 2024 en € 114,00 in 2025 en 2026',
    () => omgeving.verwachtEnergiePerJaar('Jan de Vries',
      { 2021: '-95.00', 2022: '-171.00', 2023: '-142.50', 2024: '-118.75', 2025: '-114.00', 2026: '-114.00' }, '2021-10', '2026-12'),
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
