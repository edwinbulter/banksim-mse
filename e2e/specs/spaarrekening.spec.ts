import { test } from '../dsl/fixtures';

/**
 * S8.1: rente op de spaarrekening (FO "Spaarrekening scherm"): 3% per jaar, dagelijks over het eindsaldo, maandelijks
 * bijgeschreven op de laatste dag van de maand. De simulatiedatum geldt voor de hele bank: deze scenario's draaien na de
 * andere, één voor één (ADR 0014).
 */
test.describe('Spaarrekening en rente', { tag: '@simulatiedatum' }, () => {
  // € 10.000,00 × 0,03 / 365 × 31 dagen = € 25,4794520548, afgerond € 25,48.
  test('Rente wordt maandelijks bijgeschreven', async ({ bank }) => {
    const jan = bank.klant('Jan');
    await bank.gegeven('Jan heeft op 1 januari 2026 een spaarrekening geopend met € 10.000,00', () =>
      jan.heeftSpaarrekening({ geopendOp: '2026-01-01', saldo: '10000.00' }),
    );
    await bank.en('de beheerder zet de simulatiedatum op 1 februari 2026', () =>
      bank.beheerder().zetSimulatiedatum('2026-02-01'),
    );
    await bank.als('Jan de spaarrekening opent', () => jan.opentSpaarrekening());
    await bank.dan('staat er op 31 januari 2026 een mutatie "Rente" van +25,48', () =>
      jan.verwachtSpaarmutatie({ op: '2026-01-31', type: 'Rente', bedrag: '25.48' }),
    );
  });

  // Na de rente van januari (+25,48) is het saldo € 10.025,48. De lopende rente telt van 1 februari tot en met de
  // simulatiedatum (FO): € 10.025,48 × 0,03 / 365 × 15 dagen = € 12,3601808219, afgerond € 12,36.
  test('Lopende rente wordt apart getoond', async ({ bank }) => {
    const jan = bank.klant('Jan');
    await bank.gegeven('Jan heeft sinds 1 januari 2026 € 10.000,00 op de spaarrekening', () =>
      jan.heeftSpaarrekening({ geopendOp: '2026-01-01', saldo: '10000.00' }),
    );
    await bank.en('de beheerder zet de simulatiedatum op 15 februari 2026', () =>
      bank.beheerder().zetSimulatiedatum('2026-02-15'),
    );
    await bank.als('Jan de spaarrekening opent', () => jan.opentSpaarrekening());
    await bank.dan('ziet Jan een lopende rente van € 12,36', () => jan.verwachtLopendeRente('12.36'));
  });

  // FO: "Bij de overgang naar een vorig jaar staat een tussenregel met het jaartal." De maandafsluiting schrijft rente bij
  // op 30 november 2025, 31 december 2025 en 31 januari 2026.
  test('Jaartal-tussenregel', async ({ bank }) => {
    const jan = bank.klant('Jan');
    await bank.gegeven('Jan heeft sinds 1 november 2025 € 10.000,00 op de spaarrekening', () =>
      jan.heeftSpaarrekening({ geopendOp: '2025-11-01', saldo: '10000.00' }),
    );
    await bank.en('de beheerder zet de simulatiedatum op 1 februari 2026', () =>
      bank.beheerder().zetSimulatiedatum('2026-02-01'),
    );
    await bank.als('Jan de spaarrekening opent', () => jan.opentSpaarrekening());
    await bank.dan('staat er een tussenregel "2025" tussen 31 januari 2026 en 31 december 2025', () =>
      jan.verwachtJaartalregel({ jaar: 2025, na: '2026-01-31', voor: '2025-12-31' }),
    );
  });
});
