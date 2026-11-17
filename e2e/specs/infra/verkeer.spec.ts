import { test } from '../../dsl/fixtures';

/**
 * Doorlopend verkeer met k6 (ADR 0055). De verkeersklanten betalen alleen elkaar, dus het geld blijft in de kring. In CI
 * draait dit in de job telemetrie, met otel-lgtm (ADR 0054).
 */

// Na elkaar, in één worker: een tweede run van k6 tegelijk zou de saldi verschuiven terwijl het eerste scenario ze telt.
test.describe.configure({ mode: 'default' });
test('Bij doorlopend verkeer blijft het geld in de kring', async ({ omgeving }) => {
  await omgeving.gegeven('de verkeersklanten verkeer-1 tot en met verkeer-5, die elkaar als contact hebben', () =>
    omgeving.heeftVerkeersklanten(),
  );
  await omgeving.en('het totaal van hun saldi is T', () => omgeving.onthoudTotaalVanDeVerkeersklanten());
  await omgeving.als('k6 een halve minuut verkeer maakt, met 2 klantbezoeken per seconde', () =>
    omgeving.maaktVerkeer({ snelheid: 2, duur: '30s' }),
  );
  await omgeving.dan('slaagt de run van k6, met minder dan 1% fouten', () => omgeving.verwachtGeslaagdVerkeer());
  await omgeving.en('is het totaal van de saldi van de verkeersklanten nog steeds T', () =>
    omgeving.verwachtHetzelfdeTotaalVanDeVerkeersklanten(),
  );
  await omgeving.en('heeft bank-bff in die halve minuut minstens 30 verzoeken geteld', () =>
    omgeving.verwachtVerzoekenSindsHetVerkeer('bank-bff', 30),
  );
});

test('De cijfers van k6 staan per stap in Prometheus, zonder IBAN of token', async ({ omgeving }) => {
  await omgeving.gegeven('de verkeersklanten', () => omgeving.heeftVerkeersklanten());
  await omgeving.als('k6 een halve minuut verkeer maakt, met 2 klantbezoeken per seconde', () =>
    omgeving.maaktVerkeer({ snelheid: 2, duur: '30s' }),
  );
  await omgeving.dan('heeft k6_http_reqs_total hoogstens 50 reeksen', () =>
    omgeving.verwachtHoogstensReeksenVanK6('k6_http_reqs_total', 50),
  );
  await omgeving.en('staat in geen enkel label van k6 een IBAN, een querystring of een token ("eyJ")', () =>
    omgeving.verwachtGeenIbanOfTokenInDeCijfersVanK6(),
  );
  await omgeving.en(
    'heeft elke reeks het label naam, met een van: inlogpagina, inloggen, overzicht, transacties, contacten, betalen, uitloggen, uitloggen bij Keycloak',
    () =>
      omgeving.verwachtElkeReeksVanK6PerStap('k6_http_reqs_total', [
        'inlogpagina', 'inloggen', 'overzicht', 'transacties', 'contacten', 'betalen', 'uitloggen', 'uitloggen bij Keycloak',
      ]),
  );
});
