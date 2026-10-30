import { test } from '../dsl/fixtures';

/**
 * S9.1: geld overschrijven tussen de eigen betaal- en spaarrekening (FO "Overschrijven scherm"). Twee gekoppelde
 * boekingen op "vandaag": op de betaalrekening een "Overschrijving", op de spaarrekening een "Inleg" of "Opname".
 */
test.describe('Overschrijven', () => {
  test.beforeEach(async ({ bank }) => {
    const jan = bank.klant('Jan');
    await bank.gegeven('Jan heeft € 1.000,00 op de betaalrekening en € 5.000,00 op de spaarrekening', async () => {
      await jan.heeftBetaalrekening({ saldo: '1000.00' });
      await jan.heeftSpaarrekening({ saldo: '5000.00' });
    });
  });

  test('Inleggen', async ({ bank }) => {
    const jan = bank.klant('Jan');
    await bank.als('Jan € 250,00 inlegt op de spaarrekening', () => jan.legtIn('250.00'));
    await bank.dan('staat op de spaarrekening een mutatie "Inleg" van +250,00', () =>
      jan.verwachtSpaarmutatie({ op: 'vandaag', type: 'Inleg', bedrag: '250.00' }),
    );
    await bank.en('staat op de betaalrekening een "Overschrijving" van −250,00 naar de eigen spaarrekening', () =>
      jan.verwachtOverschrijvingNaarSpaarrekening('-250.00'),
    );
    await bank.en('is in het overzicht het betaalsaldo € 750,00 en het spaarsaldo € 5.250,00', () =>
      jan.verwachtSaldi({ betaal: '750.00', spaar: '5250.00' }),
    );
  });

  test('Opnemen', async ({ bank }) => {
    const jan = bank.klant('Jan');
    await bank.als('Jan € 500,00 opneemt van de spaarrekening', () => jan.neemtOp('500.00'));
    await bank.dan('staat op de spaarrekening een mutatie "Opname" van −500,00', () =>
      jan.verwachtSpaarmutatie({ op: 'vandaag', type: 'Opname', bedrag: '-500.00' }),
    );
    await bank.en('staat op de betaalrekening een "Overschrijving" van +500,00 van de eigen spaarrekening', () =>
      jan.verwachtOverschrijvingVanSpaarrekening('500.00'),
    );
    await bank.en('is in het overzicht het betaalsaldo € 1.500,00 en het spaarsaldo € 4.500,00', () =>
      jan.verwachtSaldi({ betaal: '1500.00', spaar: '4500.00' }),
    );
  });

  test('Opnemen kan niet meer dan het spaarsaldo', async ({ bank }) => {
    const jan = bank.klant('Jan');
    await bank.als('Jan € 5.000,01 probeert op te nemen', () => jan.probeertOpTeNemen('5000.01'));
    await bank.dan('krijgt Jan de melding "Saldo ontoereikend"', () => jan.verwachtWeigering('saldo-ontoereikend'));
    await bank.en('is in het overzicht het betaalsaldo € 1.000,00 en het spaarsaldo € 5.000,00', () =>
      jan.verwachtSaldi({ betaal: '1000.00', spaar: '5000.00' }),
    );
  });
});

/**
 * TO §7: na een inleg of opname rekent de bank de rente van die maand en alle latere maanden opnieuw uit, en boekt het
 * verschil als "Rentecorrectie"; bestaande boekingen blijven staan. De simulatiedatum geldt voor de hele bank: dit
 * scenario draait na de andere, één voor één (ADR 0014).
 */
test.describe('Overschrijven in het verleden', { tag: '@simulatiedatum' }, () => {
  // Zonder opname: rente januari 12,74, februari 11,54, maart 12,80 (saldo 5.037,08).
  // Met de opname van € 1.000,00 op 15 februari: februari 10,39 en maart 10,25.
  // Het verschil is −1,15 voor februari en −2,55 voor maart; het saldo wordt 5.037,08 − 1.000,00 − 1,15 − 2,55 = 4.033,38.
  test('Opname in het verleden corrigeert de latere rente', async ({ bank }) => {
    const jan = bank.klant('Jan');
    await bank.gegeven('Jan heeft een betaalrekening en sinds 1 januari 2026 € 5.000,00 op de spaarrekening', async () => {
      await jan.heeftBetaalrekening({ saldo: '0.00' });
      await jan.heeftSpaarrekening({ geopendOp: '2026-01-01', saldo: '5000.00' });
    });
    await bank.en('de beheerder heeft de simulatiedatum op 1 april 2026 gezet, zodat de rente t/m maart is bijgeschreven', () =>
      bank.beheerder().zetSimulatiedatum('2026-04-01'),
    );
    await bank.en('de beheerder zet de simulatiedatum terug op 15 februari 2026', () =>
      bank.beheerder().zetSimulatiedatum('2026-02-15'),
    );
    await bank.als('Jan € 1.000,00 opneemt', () => jan.neemtOp('1000.00'));
    await bank.en('de beheerder zet de simulatiedatum op 1 april 2026', () =>
      bank.beheerder().zetSimulatiedatum('2026-04-01'),
    );
    await bank.dan('staat er op 28 februari 2026 een "Rentecorrectie februari" van −1,15', () =>
      jan.verwachtRentecorrectie({ op: '2026-02-28', maand: 'februari', bedrag: '-1.15' }),
    );
    await bank.en('staat er op 31 maart 2026 een "Rentecorrectie maart" van −2,55', () =>
      jan.verwachtRentecorrectie({ op: '2026-03-31', maand: 'maart', bedrag: '-2.55' }),
    );
    await bank.en('is het spaarsaldo € 4.033,38', () => jan.verwachtSaldi({ betaal: '1000.00', spaar: '4033.38' }));
  });
});
