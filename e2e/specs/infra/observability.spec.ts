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

test('Een geweigerde betaling is terug te vinden in de melding, de logs en de trace', async ({ omgeving, bank }) => {
  const jan = bank.klant('Jan');
  const piet = bank.klant('Piet');
  await omgeving.gegeven('BankSim-MSE is geïnstalleerd', () => omgeving.installeerAlsDatNodigIs());
  await omgeving.en('klant Jan met een betaalrekening met saldo 10,00 en contact Piet', async () => {
    await jan.heeftBetaalrekening({ saldo: '10.00' });
    await piet.heeftBetaalrekening({ contact: true });
  });
  await omgeving.als('Jan 25,00 probeert te betalen aan Piet', () => jan.probeertTeBetalen({ bedrag: '25.00', aan: piet }));
  await omgeving.dan('krijgt Jan de melding "Saldo ontoereikend", met een trace-ID', async () => {
    await jan.verwachtWeigering('saldo-ontoereikend');
    await omgeving.verwachtTraceIdInDeMelding(jan);
  });
  await omgeving.en('staat in Loki een logregel van bank-api over de geweigerde betaling, met dat trace-ID', () =>
    omgeving.verwachtLogregelBijDeTrace('bank-api', 'geweigerd'),
  );
  await omgeving.en('staat die trace in Tempo', () => omgeving.verwachtDeTraceInTempo());
});

test('De telemetrie bevat geen IBAN en geen naam van een klant', async ({ omgeving, bank }) => {
  const jan = bank.klant('Jan');
  const piet = bank.klant('Piet');
  await omgeving.gegeven('BankSim-MSE is geïnstalleerd', () => omgeving.installeerAlsDatNodigIs());
  await omgeving.en('klant Jan met een betaalrekening met saldo 100,00 en contact Piet', async () => {
    await jan.heeftBetaalrekening({ saldo: '100.00' });
    await piet.heeftBetaalrekening({ contact: true });
  });
  await omgeving.als('Jan 12,50 betaalt aan Piet', () => jan.betaalt({ bedrag: '12.50', aan: piet }));
  await omgeving.en('Jan zijn transacties opent', () => jan.opentBetaalrekening());
  await omgeving.en('Jan in zijn contacten zoekt op "Piet"', () => jan.zoektContact('Piet'));
  await omgeving.dan('staat Jans IBAN nergens in Tempo of Loki', async () => {
    await omgeving.verwachtEenTraceVanDeBetaling(jan);
    await omgeving.verwachtNietInDeTelemetrie(jan.iban(), 'het IBAN van Jan');
  });
  await omgeving.en('staat de naam van Piet nergens in Tempo of Loki', () =>
    omgeving.verwachtNietInDeTelemetrie(piet.naam(), 'de naam van Piet'),
  );
});

test('Een geweigerde betaling telt mee in de metrics', async ({ omgeving, bank }) => {
  const jan = bank.klant('Jan');
  const sanne = bank.klant('Sanne');
  await omgeving.gegeven('BankSim-MSE is geïnstalleerd', () => omgeving.installeerAlsDatNodigIs());
  await omgeving.en('klant Jan met een betaalrekening met saldo 100,00 en contact Sanne', async () => {
    await jan.heeftBetaalrekening({ saldo: '100.00' });
    await sanne.heeftBetaalrekening({ contact: true });
  });
  await omgeving.en('de bank telde N weigeringen met reden "naam-past-niet"', () => omgeving.onthoudWeigeringen('naam-past-niet'));
  await omgeving.als('Jan 25,00 probeert te betalen aan Sanne, met de naam Piet', () =>
    jan.probeertTeBetalen({ bedrag: '25.00', aan: sanne, naam: 'Piet' }),
  );
  await omgeving.dan('telt de bank N + 1 weigeringen met reden "naam-past-niet"', () =>
    omgeving.verwachtEenWeigeringMeer('naam-past-niet'),
  );
});

test('CPU en geheugen van de processen komen aan', async ({ omgeving }) => {
  await omgeving.gegeven('BankSim-MSE is geïnstalleerd', () => omgeving.installeerAlsDatNodigIs());
  await omgeving.dan('zijn er CPU- en geheugenmetrics van bank-api, bank-bff en Keycloak', () =>
    omgeving.verwachtCpuEnGeheugenVan(['bank-api', 'bank-bff', 'keycloak']),
  );
});

test('Het dashboard staat klaar', async ({ omgeving }) => {
  await omgeving.gegeven('BankSim-MSE is geïnstalleerd', () => omgeving.installeerAlsDatNodigIs());
  await omgeving.dan('heeft Grafana het dashboard "BankSim-MSE"', () => omgeving.verwachtDashboard('BankSim-MSE'));
});
