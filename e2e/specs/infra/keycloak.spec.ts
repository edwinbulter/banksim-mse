import { test } from '../../dsl/fixtures';

/**
 * Keycloak weigert een onveilige login (doc/werkwijze-mse.md, hoofdstuk 7, CFR OWASP Top 10; ADR 0008).
 * Elk scenario vraagt bij Keycloak een login aan voor client bank-bff, zoals de BFF dat doet, en verandert één ding.
 * Het eerste scenario laat zien dat de aanvraag zonder die verandering wel het inlogscherm krijgt; anders zouden de
 * andere ook groen zijn bij een aanvraag die om een andere reden niet klopt.
 */
const EIGEN = 'https://bank-mse.localtest.me/login/oauth2/code/keycloak';
const VREEMD = 'https://evil.example/login/oauth2/code/keycloak';

test('Een login met PKCE en de eigen redirect-URI krijgt het inlogscherm', async ({ omgeving }) => {
  const keycloak = omgeving.keycloak();
  await omgeving.gegeven('BankSim-MSE is geïnstalleerd', () => omgeving.installeerAlsDatNodigIs());
  await omgeving.als(`een browser bij Keycloak inlogt voor bank-bff met PKCE (S256) en redirect-URI ${EIGEN}`, () =>
    keycloak.vraagtLoginAan({ pkce: 'S256', redirectUri: EIGEN }),
  );
  await omgeving.dan('toont Keycloak het inlogscherm van realm banksim-mse', () => keycloak.verwachtInlogscherm());
});

test('Een login zonder PKCE wordt geweigerd', async ({ omgeving }) => {
  const keycloak = omgeving.keycloak();
  await omgeving.gegeven('BankSim-MSE is geïnstalleerd', () => omgeving.installeerAlsDatNodigIs());
  await omgeving.als('een browser bij Keycloak inlogt voor bank-bff zonder code_challenge en met de eigen redirect-URI', () =>
    keycloak.vraagtLoginAan({ redirectUri: EIGEN }),
  );
  await omgeving.dan('toont Keycloak geen inlogscherm', () => keycloak.verwachtGeenInlogscherm());
  await omgeving.en('stuurt Keycloak terug naar de eigen redirect-URI met error=invalid_request', () =>
    keycloak.verwachtTeruggestuurdMetFout(EIGEN, 'invalid_request'),
  );
});

test('Een login met PKCE-methode plain wordt geweigerd', async ({ omgeving }) => {
  const keycloak = omgeving.keycloak();
  await omgeving.gegeven('BankSim-MSE is geïnstalleerd', () => omgeving.installeerAlsDatNodigIs());
  await omgeving.als('een browser bij Keycloak inlogt voor bank-bff met PKCE (plain) en met de eigen redirect-URI', () =>
    keycloak.vraagtLoginAan({ pkce: 'plain', redirectUri: EIGEN }),
  );
  await omgeving.dan('toont Keycloak geen inlogscherm', () => keycloak.verwachtGeenInlogscherm());
  await omgeving.en('stuurt Keycloak terug naar de eigen redirect-URI met error=invalid_request', () =>
    keycloak.verwachtTeruggestuurdMetFout(EIGEN, 'invalid_request'),
  );
});

test('Een login met een vreemde redirect-URI wordt geweigerd', async ({ omgeving }) => {
  const keycloak = omgeving.keycloak();
  await omgeving.gegeven('BankSim-MSE is geïnstalleerd', () => omgeving.installeerAlsDatNodigIs());
  await omgeving.als(`een browser bij Keycloak inlogt voor bank-bff met PKCE (S256) en redirect-URI ${VREEMD}`, () =>
    keycloak.vraagtLoginAan({ pkce: 'S256', redirectUri: VREEMD }),
  );
  await omgeving.dan('toont Keycloak geen inlogscherm', () => keycloak.verwachtGeenInlogscherm());
  await omgeving.en('stuurt Keycloak niet door naar evil.example', () => keycloak.verwachtNietDoorgestuurdNaar('evil.example'));
});
