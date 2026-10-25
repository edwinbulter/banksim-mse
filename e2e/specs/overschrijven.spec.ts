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
});
