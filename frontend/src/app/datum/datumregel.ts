const NOTATIE = /^\d{4}-\d{2}-\d{2}$/;

const OPMAAK = new Intl.DateTimeFormat('nl-NL', {
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  year: 'numeric',
  // De datum is een kalenderdag zonder tijd; in UTC lezen en opmaken voorkomt een verschoven dag.
  timeZone: 'UTC',
});

const ZONDER_DAG = new Intl.DateTimeFormat('nl-NL', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' });

/** "2026-10-02" → "Vrijdag 2 oktober 2026": de kop boven de transacties van één dag (S2.1). */
export function datumregel(datum: string): string {
  const regel = OPMAAK.format(alsDag(datum));
  return regel.charAt(0).toUpperCase() + regel.slice(1);
}

/** "2021-10-01" → "1 oktober 2021", zoals in "Beschikbaar: 1 oktober 2021 t/m 31 december 2026" (S10.2). */
export function datumInTekst(datum: string): string {
  return ZONDER_DAG.format(alsDag(datum));
}

function alsDag(datum: string): Date {
  const dag = new Date(`${datum}T00:00:00Z`);
  if (!NOTATIE.test(datum) || Number.isNaN(dag.getTime()) || dag.toISOString().slice(0, 10) !== datum) {
    throw new Error(`Geen datum: ${datum}`);
  }
  return dag;
}
