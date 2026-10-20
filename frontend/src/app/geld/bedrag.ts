const NOTATIE = /^(-?)(\d+)\.(\d{2})$/;

/**
 * "1842.17" → "€ 1.842,17" (ADR 0010). Alleen tekst omzetten: een bedrag wordt in de frontend nooit een
 * number, dus ook niet afgerond.
 */
export function bedragVoorWeergave(bedrag: string): string {
  const delen = NOTATIE.exec(bedrag);
  if (!delen) {
    throw new Error(`Geen bedrag met 2 decimalen: ${bedrag}`);
  }
  const [, min, euros, centen] = delen;
  const metPunten = euros.replace(/\B(?=(\d{3})+$)/g, '.');
  return `${min ? '−' : ''}€ ${metPunten},${centen}`;
}
