import { expect, type Page } from '@playwright/test';
import type { Transactie, TransactiesOpDatum } from '../bank-driver';

const MAANDEN = [
  'januari', 'februari', 'maart', 'april', 'mei', 'juni',
  'juli', 'augustus', 'september', 'oktober', 'november', 'december',
];

/** De betaalrekening: per datum een kop ("Vrijdag 2 oktober 2026") met de transacties van die dag. */
export class BetaalrekeningPagina {
  constructor(private readonly page: Page) {}

  async open(iban: string): Promise<void> {
    await this.page.getByRole('list', { name: 'Betaalrekeningen' }).getByRole('button', { name: iban }).click();
    await expect(this.page.getByRole('heading', { name: 'Betaalrekening' })).toBeVisible();
  }

  async transacties(): Promise<TransactiesOpDatum[]> {
    const lijst = this.page.getByRole('list', { name: 'Transacties' });
    await expect(lijst).toBeVisible();
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
