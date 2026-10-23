/** De labels van de 7 transactietypen uit het FO. */
const LABELS: Record<string, string> = {
  ONLINE_BANKIEREN: 'Online bankieren',
  IDEAL_WERO: 'iDEAL | Wero',
  BETAALAUTOMAAT: 'Betaalautomaat',
  INCASSO: 'Incasso',
  GELDAUTOMAAT: 'Geldautomaat',
  OVERSCHRIJVING: 'Overschrijving',
  VERZAMELBETALING: 'Verzamelbetaling',
};

/** S4.1: het type zoals de API het levert ("INCASSO") → de tekst in de details ("Incasso"). */
export function typeVoorWeergave(type: string): string {
  const label = LABELS[type];
  if (label === undefined) {
    throw new Error(`Onbekend transactietype: ${type}`);
  }
  return label;
}
