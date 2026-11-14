import { test } from '../../dsl/fixtures';

/**
 * De browser krijgt geen token van de bank (doc/werkwijze-mse.md, hoofdstuk 7, CFR OWASP Top 10; ADR 0008). Keycloak
 * geeft elk token uit als JWT; de drivers zoeken naar dat patroon. Het SSO-cookie dat Keycloak op zijn eigen host zet,
 * telt niet mee: dat komt niet van de bank. De enige uitzondering is het ID-token in de uitlog-URL (ADR 0021).
 */
test('Tijdens het inloggen en het bekijken van het overzicht krijgt de browser geen token', async ({ omgeving, bank }) => {
  const jan = bank.klant('Jan');
  await omgeving.gegeven('BankSim-MSE is geïnstalleerd', () => omgeving.installeerAlsDatNodigIs());
  await omgeving.en('Jan is een klant van de bank', () => jan.heeftBetaalrekening());
  await omgeving.als('Jan inlogt via https://bank-mse.localtest.me', () => jan.logtInEnBewaartCookies());
  await omgeving.en('Jan zijn overzicht opent', () => jan.opentOverzicht());
  await omgeving.dan('staat er in geen enkel antwoord van de bank een token, niet in de headers en niet in de inhoud', () =>
    jan.verwachtGeenTokenVanDeBank(),
  );
});

test('Na het inloggen bewaart de browser geen token', async ({ omgeving, bank }) => {
  const jan = bank.klant('Jan');
  await omgeving.gegeven('BankSim-MSE is geïnstalleerd', () => omgeving.installeerAlsDatNodigIs());
  await omgeving.en('Jan is een klant van de bank', () => jan.heeftBetaalrekening());
  await omgeving.als('Jan in de browser inlogt via https://bank-mse.localtest.me', () => jan.logtInInDeBrowser());
  await omgeving.en('Jan zijn overzicht ziet', () => jan.zietZijnOverzichtInDeBrowser());
  await omgeving.dan('staat er geen token in de cookies, de localStorage of de sessionStorage van de bank', () =>
    jan.verwachtGeenTokenInDeBrowser(),
  );
});

test('Bij het uitloggen krijgt de browser alleen het ID-token, als hint voor Keycloak', async ({ omgeving, bank }) => {
  const jan = bank.klant('Jan');
  await omgeving.gegeven('BankSim-MSE is geïnstalleerd', () => omgeving.installeerAlsDatNodigIs());
  await omgeving.en('Jan is een klant van de bank', () => jan.heeftBetaalrekening());
  await omgeving.en('Jan is ingelogd', () => jan.opentOverzicht());
  await omgeving.als('Jan uitlogt', () => jan.logtUitEnBewaartTokens());
  await omgeving.dan('is het enige token in het antwoord van de bank Jans ID-token, in de uitlog-URL van Keycloak', () =>
    jan.verwachtAlleenEigenIdTokenInUitlogUrl(),
  );
  await omgeving.en('krijgt de browser geen access token en geen refresh token', () =>
    jan.verwachtGeenAccessOfRefreshTokenBijUitloggen(),
  );
  await omgeving.en('kan het ID-token bank-api niet aanroepen', () => jan.verwachtIdTokenNietVoorBankApi());
});
