/** De 7 transactietypen uit het FO, met hun label, in de volgorde van het FO. */
export const TRANSACTIETYPEN = [
  { code: 'ONLINE_BANKIEREN', label: 'Online bankieren' },
  { code: 'IDEAL_WERO', label: 'iDEAL | Wero' },
  { code: 'BETAALAUTOMAAT', label: 'Betaalautomaat' },
  { code: 'INCASSO', label: 'Incasso' },
  { code: 'GELDAUTOMAAT', label: 'Geldautomaat' },
  { code: 'OVERSCHRIJVING', label: 'Overschrijving' },
  { code: 'VERZAMELBETALING', label: 'Verzamelbetaling' },
] as const;

const LABELS = new Map<string, string>(TRANSACTIETYPEN.map(({ code, label }) => [code, label]));

/** S4.1: het type zoals de API het levert ("INCASSO") → de tekst in de details ("Incasso"). */
export function typeVoorWeergave(type: string): string {
  const label = LABELS.get(type);
  if (label === undefined) {
    throw new Error(`Onbekend transactietype: ${type}`);
  }
  return label;
}
