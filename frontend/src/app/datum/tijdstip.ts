const DATUM = { day: 'numeric', month: 'long', year: 'numeric' } as const;

/** De klok van de bank, niet die van de browser (zie ADR 0013). */
const IN_AMSTERDAM = new Intl.DateTimeFormat('nl-NL', {
  ...DATUM,
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
  timeZone: 'Europe/Amsterdam',
});

// Een kalenderdag zonder tijd; in UTC lezen en opmaken voorkomt een verschoven dag (zoals datumregel).
const DAG = new Intl.DateTimeFormat('nl-NL', { ...DATUM, timeZone: 'UTC' });

/** S4.1: "2026-10-01T06:02:00+02:00" → "1 oktober 2026 om 06:02", in Europe/Amsterdam. */
export function tijdstipVoorWeergave(tijdstip: string): string {
  const moment = new Date(tijdstip);
  if (Number.isNaN(moment.getTime())) {
    throw new Error(`Geen tijdstip: ${tijdstip}`);
  }
  const deel = (soort: Intl.DateTimeFormatPartTypes) =>
    IN_AMSTERDAM.formatToParts(moment).find((p) => p.type === soort)?.value ?? '';
  return `${deel('day')} ${deel('month')} ${deel('year')} om ${deel('hour')}:${deel('minute')}`;
}

/** S4.1: "2026-10-01" → "1 oktober 2026". */
export function datumZonderDag(datum: string): string {
  return DAG.format(new Date(`${datum}T00:00:00Z`));
}
