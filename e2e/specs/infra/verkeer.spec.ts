import { test } from '../../dsl/fixtures';

/**
 * Doorlopend verkeer met k6 (ADR 0055). De verkeersklanten betalen alleen elkaar, dus het geld blijft in de kring. In CI
 * draait dit in de job telemetrie, met otel-lgtm (ADR 0054).
 */
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
