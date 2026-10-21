import { expect, type Page } from '@playwright/test';
import type { Transactie, TransactiesOpDatum } from '../bank-driver';

const MAANDEN = [
  'januari', 'februari', 'maart', 'april', 'mei', 'juni',
  'juli', 'augustus', 'september', 'oktober', 'november', 'december',
];

/**
 * De betaalrekening: per datum een kop ("Vrijdag 2 oktober 2026") met de transacties van die dag.
 * Zonder transacties staat er "Nog geen af- of bijschrijvingen."; dat is een lege lijst.
 */
export class BetaalrekeningPagina {
  constructor(private readonly page: Page) {}

  async open(iban: string): Promise<void> {
    await this.page.getByRole('list', { name: 'Betaalrekeningen' }).getByRole('button', { name: iban }).click();
    await expect(this.page.getByRole('heading', { name: 'Betaalrekening' })).toBeVisible();
  }

  async transacties(): Promise<TransactiesOpDatum[]> {
    const lijst = this.page.getByRole('list', { name: 'Transacties' });
    const leeg = this.page.getByText('Nog geen af- of bijschrijvingen.');
    await expect(lijst.or(leeg)).toBeVisible();
    if (await leeg.isVisible()) return [];
    const perDatum: TransactiesOpDatum[] = [];
    for (const dag of await lijst.locator(':scope > li').all()) {
      const datum = datumUitKop((await dag.getByRole('heading').textContent()) ?? '');
      const transacties: Transactie[] = [];
      for (const regel of await dag.getByRole('listitem').all()) {
        transacties.push(transactieUitRegel((await regel.textContent()) ?? ''));
      }
      perDatum.push({ datum, transacties });
    }
    return perDatum;
  }

  /** Pas na transacties() betrouwbaar: dan is de lijst geladen en staat de knop er, of niet. */
  async heeftMeer(): Promise<boolean> {
    return this.toonMeerKnop().isVisible();
  }

  /** Klikt "Toon meer" en wacht tot er transactieregels bij zijn gekomen. */
  async toonMeer(): Promise<void> {
    const regels = this.page.getByRole('list', { name: 'Transacties' }).locator(':scope > li li');
    const voor = await regels.count();
    await this.toonMeerKnop().click();
    await expect.poll(() => regels.count(), 'transactieregels na "Toon meer"').toBeGreaterThan(voor);
  }

  /** De link "Overzicht" bovenaan, zoals "< Overzicht" in het origineel. */
  async kiesOverzicht(): Promise<void> {
    await this.page.getByRole('link', { name: 'Overzicht' }).click();
  }

  private toonMeerKnop() {
    return this.page.getByRole('button', { name: 'Toon meer' });
  }
}

/** "Vrijdag 2 oktober 2026" → "2026-10-02". */
function datumUitKop(kop: string): string {
  const delen = /^\S+\s+(\d{1,2})\s+(\S+)\s+(\d{4})$/.exec(kop.trim());
  const maand = delen ? MAANDEN.indexOf(delen[2]) + 1 : 0;
  if (!delen || maand === 0) throw new Error(`Geen datumkop: "${kop}"`);
  return `${delen[3]}-${String(maand).padStart(2, '0')}-${delen[1].padStart(2, '0')}`;
}

/** "Werkgever B.V. +3.215,00" → { tegenpartij: "Werkgever B.V.", bedrag: "3215.00" }; − is het echte minteken. */
function transactieUitRegel(regel: string): Transactie {
  const delen = /^(.*\S)\s+([+−])([\d.]+,\d{2})$/.exec(regel.replace(/\s+/g, ' ').trim());
  if (!delen) throw new Error(`Geen transactieregel: "${regel}"`);
  const bedrag = delen[3].replace(/\./g, '').replace(',', '.');
  return { tegenpartij: delen[1], bedrag: `${delen[2] === '−' ? '-' : ''}${bedrag}` };
}
