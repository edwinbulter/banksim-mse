const NOTATIE = /^(-?)(\d+)\.(\d{2})$/;

/**
 * "1842.17" → "€ 1.842,17" (ADR 0010). Alleen tekst omzetten: een bedrag wordt in de frontend nooit een
 * number, dus ook niet afgerond.
 */
export function bedragVoorWeergave(bedrag: string): string {
  const { negatief, euros, centen } = delen(bedrag);
  return `${negatief ? '−' : ''}€ ${euros},${centen}`;
}

/**
 * "3215.00" → "+3.215,00" en "-812.40" → "−812,40" (S2.1): met teken en zonder euroteken, voor de transactielijst.
 * Het minteken is het echte minteken (U+2212), net als in {@link bedragVoorWeergave}.
 */
export function bedragMetTeken(bedrag: string): string {
  const { negatief, euros, centen } = delen(bedrag);
  return `${negatief ? '−' : '+'}${euros},${centen}`;
}

/** Teken, euro's met punten per duizendtal, en centen; als tekst. */
function delen(bedrag: string): { negatief: boolean; euros: string; centen: string } {
  const match = NOTATIE.exec(bedrag);
  if (!match) {
    throw new Error(`Geen bedrag met 2 decimalen: ${bedrag}`);
  }
  const [, min, euros, centen] = match;
  return { negatief: min === '-', euros: euros.replace(/\B(?=(\d{3})+$)/g, '.'), centen };
}

/** "25,00" → "25.00" (S7.1): het bedrag zoals de klant het typt, naar de notatie van de API (ADR 0010). */
export function bedragUitInvoer(invoer: string): string {
  return invoer.replace(',', '.');
}
