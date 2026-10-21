const NOTATIE = /^\d{4}-\d{2}-\d{2}$/;

const OPMAAK = new Intl.DateTimeFormat('nl-NL', {
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  year: 'numeric',
  // De datum is een kalenderdag zonder tijd; in UTC lezen en opmaken voorkomt een verschoven dag.
  timeZone: 'UTC',
});

/** "2026-10-02" → "Vrijdag 2 oktober 2026": de kop boven de transacties van één dag (S2.1). */
export function datumregel(datum: string): string {
  const dag = new Date(`${datum}T00:00:00Z`);
  if (!NOTATIE.test(datum) || Number.isNaN(dag.getTime()) || dag.toISOString().slice(0, 10) !== datum) {
    throw new Error(`Geen datum: ${datum}`);
  }
  const regel = OPMAAK.format(dag);
  return regel.charAt(0).toUpperCase() + regel.slice(1);
}
