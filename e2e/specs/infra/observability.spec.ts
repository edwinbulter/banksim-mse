import { test } from '../../dsl/fixtures';

/**
 * Telemetrie (ADR 0054; doc/observability-keuzes.md, CFR-slice): wat de bank doet, is terug te vinden in Grafana. De
 * scenario's zoeken in Tempo, dat in otel-lgtm in de namespace draait.
 */
test('Een betaling is als één trace te volgen, van de BFF tot de database', async ({ omgeving, bank }) => {
  const jan = bank.klant('Jan');
  const piet = bank.klant('Piet');
  await omgeving.gegeven('BankSim-MSE is geïnstalleerd', () => omgeving.installeerAlsDatNodigIs());
  await omgeving.en('klant Jan met een betaalrekening met saldo 100,00 en contact Piet', async () => {
    await jan.heeftBetaalrekening({ saldo: '100.00' });
    await piet.heeftBetaalrekening({ contact: true });
  });
  await omgeving.als('Jan 12,50 betaalt aan Piet', () => jan.betaalt({ bedrag: '12.50', aan: piet }));
  await omgeving.dan('bestaat er van die betaling één trace', () => omgeving.verwachtEenTraceVanDeBetaling(jan));
  await omgeving.en('daarin zitten spans van bank-bff en van bank-api', () =>
    omgeving.verwachtSpansVanDiensten(['bank-bff', 'bank-api']),
  );
  await omgeving.en('daarin zit een SQL-query van bank-api', () => omgeving.verwachtEenSqlQueryVan('bank-api'));
});
