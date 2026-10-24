import { test } from '../dsl/fixtures';

/**
 * S6.2: na het uitloggen kan een ander op dit apparaat niet bij Jans rekeningen. Ook Keycloak vraagt dan opnieuw om
 * het wachtwoord. @rook: uitloggen hangt af van de cookies van Keycloak, dus het draait in elke browser (ADR 0019).
 */
test('Uitloggen', { tag: '@rook' }, async ({ bank }) => {
  const jan = bank.klant('Jan');
  await bank.gegeven('Jan heeft een betaalrekening met saldo € 1.842,17', () =>
    jan.heeftBetaalrekening({ saldo: '1842.17' }),
  );
  await bank.en('Jan is ingelogd en ziet het overzicht', () => jan.opentOverzicht());
  await bank.als('Jan uitlogt', () => jan.logtUit());
  await bank.dan('ziet Jan zijn rekeningen niet meer, maar "Inloggen"', () => jan.verwachtNietIngelogd());
  await bank.en('vraagt "Inloggen" opnieuw om gebruikersnaam en wachtwoord', () => jan.verwachtInlogscherm());
});
