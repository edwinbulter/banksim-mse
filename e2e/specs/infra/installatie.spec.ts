import { test } from '../../dsl/fixtures';

/**
 * Installatiescenario's (doc/werkwijze-mse.md, hoofdstuk 9.2; bewaking van ADR 0004).
 * Ze draaien na elkaar: elk scenario gaat uit van de installatie die het vorige achterlaat.
 * @destructief-scenario's verwijderen de installatie en draaien alleen met BANKSIM_DESTRUCTIEF=1 (het CI-cluster).
 */
test.describe.configure({ mode: 'serial' });

test('Installeren maakt BankSim-MSE bereikbaar', { tag: '@destructief' }, async ({ omgeving }) => {
  await omgeving.gegeven('namespace banksim-mse bestaat niet', () => omgeving.verwijder());
  await omgeving.als('de beheerder BankSim-MSE installeert', () => omgeving.installeer());
  await omgeving.dan('antwoordt https://bank-mse.localtest.me met de geïnstalleerde versie', () =>
    omgeving.verwachtBereikbaar(),
  );
  await omgeving.en('staan alle resources van de release in namespace banksim-mse', () =>
    omgeving.verwachtAllesInNamespace(),
  );
});

test('Opnieuw installeren verandert niets', async ({ omgeving }) => {
  const voor = await omgeving.momentopname();
  await omgeving.als('de beheerder BankSim-MSE nog een keer installeert', () => omgeving.installeer());
  await omgeving.dan('zijn de resources en pods van BankSim-MSE ongewijzigd', () =>
    omgeving.verwachtOngewijzigd(voor),
  );
});

test('Een andere actieve kubectl-context wordt genegeerd', async ({ omgeving }) => {
  const revisie = await omgeving.revisie();
  await omgeving.als('de beheerder installeert terwijl kind-iets-anders de actieve context is', () =>
    omgeving.installeerMetActieveContext('kind-iets-anders'),
  );
  await omgeving.dan('installeert het script toch in kind-single-node', () =>
    omgeving.verwachtGeinstalleerdNa(revisie),
  );
});

test('Een onbekende context wordt geweigerd', async ({ omgeving }) => {
  const voor = await omgeving.momentopname();
  await omgeving.als('de beheerder installeert met --context bestaat-niet', () =>
    omgeving.installeerMetContext('bestaat-niet'),
  );
  await omgeving.dan('stopt de installatie met een foutmelding', () => omgeving.verwachtGeweigerd('bestaat-niet'));
  await omgeving.en('is BankSim-MSE ongewijzigd', () => omgeving.verwachtOngewijzigd(voor));
});

test('Het origineel in namespace banksim blijft onaangeroerd', async ({ omgeving }) => {
  test.skip(!(await omgeving.origineelAanwezig()), 'namespace banksim bestaat niet in dit cluster');
  const origineel = await omgeving.origineel().momentopname();
  await omgeving.als('de beheerder BankSim-MSE installeert', () => omgeving.installeer());
  await omgeving.dan('is namespace banksim ongewijzigd', () => omgeving.origineel().verwachtGelijkAan(origineel));
  await omgeving.en('antwoordt bank.localtest.me nog', () => omgeving.origineel().verwachtBereikbaar());
});

test('Verwijderen laat niets achter', { tag: '@destructief' }, async ({ omgeving }) => {
  const origineel = (await omgeving.origineelAanwezig()) ? await omgeving.origineel().momentopname() : undefined;
  await omgeving.als('de beheerder BankSim-MSE verwijdert', () => omgeving.verwijder());
  await omgeving.dan('bestaat namespace banksim-mse niet meer', () => omgeving.verwachtGeenNamespace());
  await omgeving.en('zijn er nergens resources met het label van de release', () =>
    omgeving.verwachtGeenResourcesVanDeRelease(),
  );
  if (origineel) {
    await omgeving.en('is namespace banksim ongewijzigd', () => omgeving.origineel().verwachtGelijkAan(origineel));
  }
  await omgeving.en('kan BankSim-MSE daarna opnieuw worden geïnstalleerd', () => omgeving.installeer());
});
