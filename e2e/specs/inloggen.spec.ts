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

/**
 * Backlog CFR OWASP Top 10: uitloggen wist de tokens van die sessie in de BFF. Een andere sessie van Jan, op een ander
 * apparaat, houdt de zijne.
 */
test('Uitloggen op het ene apparaat laat Jan op het andere ingelogd', async ({ bank }) => {
  const jan = bank.klant('Jan');
  await bank.gegeven('Jan heeft een betaalrekening met saldo € 1.842,17', () =>
    jan.heeftBetaalrekening({ saldo: '1842.17' }),
  );
  await bank.en('Jan is ingelogd op zijn laptop', () => jan.isIngelogdOp('laptop'));
  await bank.en('Jan is ingelogd op zijn telefoon', () => jan.isIngelogdOp('telefoon'));
  await bank.als('Jan op zijn telefoon uitlogt', () => jan.logtUitOp('telefoon'));
  await bank.dan('ziet Jan op zijn laptop nog zijn betaalrekening met saldo € 1.842,17', () =>
    jan.verwachtOpApparaatBetaalrekening('laptop', '1842.17'),
  );
  await bank.en('is Jan op zijn telefoon niet meer ingelogd', () => jan.verwachtNietIngelogdOp('telefoon'));
});

/**
 * CFR OWASP Top 10: Keycloak blokkeert een klant na vijf verkeerde wachtwoorden een tijd (bruteForceProtected,
 * failureFactor 5 in de realm). Het tweede scenario legt de grens vast: met failureFactor 1 zou het eerste ook groen zijn.
 * De pogingen gaan een paar seconden na elkaar, zoals een mens; anders blokkeert de snelle-login-check (derde scenario).
 */
test('Na vijf verkeerde wachtwoorden kan Jan even niet inloggen, ook niet met het juiste', async ({ bank }) => {
  const jan = bank.klant('Jan');
  await bank.gegeven('Jan heeft een betaalrekening', () => jan.heeftBetaalrekening());
  await bank.en('Jan heeft vijf keer, een paar seconden na elkaar, ingelogd met een verkeerd wachtwoord', () =>
    jan.heeftVerkeerdIngelogd(5, { rustig: true }),
  );
  await bank.als('Jan inlogt met zijn juiste wachtwoord', () => jan.logtInMetJuisteWachtwoord());
  await bank.dan('is Jan niet ingelogd', () => jan.verwachtIngelogd(false));
});

test('Na vier verkeerde wachtwoorden kan Jan nog inloggen', async ({ bank }) => {
  const jan = bank.klant('Jan');
  await bank.gegeven('Jan heeft een betaalrekening', () => jan.heeftBetaalrekening());
  await bank.en('Jan heeft vier keer, een paar seconden na elkaar, ingelogd met een verkeerd wachtwoord', () =>
    jan.heeftVerkeerdIngelogd(4, { rustig: true }),
  );
  await bank.als('Jan inlogt met zijn juiste wachtwoord', () => jan.logtInMetJuisteWachtwoord());
  await bank.dan('ziet Jan zijn overzicht', () => jan.verwachtIngelogd(true));
});

/** Snel achter elkaar is geen mens: Keycloak blokkeert dan meteen (quickLoginCheckMilliSeconds, standaard 1000 ms). */
test('Twee verkeerde wachtwoorden binnen een seconde blokkeren Jan meteen', async ({ bank }) => {
  const jan = bank.klant('Jan');
  await bank.gegeven('Jan heeft een betaalrekening', () => jan.heeftBetaalrekening());
  await bank.en('Jan heeft twee keer binnen een seconde ingelogd met een verkeerd wachtwoord', () =>
    jan.heeftVerkeerdIngelogd(2, { rustig: false }),
  );
  await bank.als('Jan inlogt met zijn juiste wachtwoord', () => jan.logtInMetJuisteWachtwoord());
  await bank.dan('is Jan niet ingelogd', () => jan.verwachtIngelogd(false));
});

/** S6.1: Keycloak weigert een verkeerd wachtwoord; Jan blijft op het inlogscherm. @ui: het gaat om wat Jan ziet. */
test('Verkeerd wachtwoord', { tag: '@ui' }, async ({ bank }) => {
  const jan = bank.klant('Jan');
  await bank.gegeven('Jan heeft een betaalrekening met saldo € 1.842,17', () =>
    jan.heeftBetaalrekening({ saldo: '1842.17' }),
  );
  await bank.als('Jan inlogt met een verkeerd wachtwoord', () => jan.logtInMetWachtwoord('fout'));
  await bank.dan('blijft Jan op het inlogscherm met "Gebruikersnaam of wachtwoord ongeldig."', () =>
    jan.verwachtInlogfout('Gebruikersnaam of wachtwoord ongeldig.'),
  );
});
