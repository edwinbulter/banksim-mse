import { test } from '../dsl/fixtures';

/** S6.1: een klant ziet alleen de eigen rekeningen; een rekening van een ander bestaat voor hem niet. */
test('Andermans rekening bestaat niet', async ({ bank }) => {
  const jan = bank.klant('Jan');
  const sanne = bank.klant('Sanne');
  await bank.gegeven('Jan heeft een betaalrekening met saldo € 1.842,17', () =>
    jan.heeftBetaalrekening({ saldo: '1842.17' }),
  );
  await bank.en('Sanne heeft een betaalrekening', () => sanne.heeftBetaalrekening());
  await bank.als('Jan de betaalrekening van Sanne probeert te openen', () => jan.opentRekening(sanne.iban()));
  await bank.dan('ziet Jan "Rekening is niet gevonden."', () => jan.verwachtNietGevonden());
});
