import { expect, type Locator, type Page } from '@playwright/test';
import type { RekeningInOverzicht, Soort } from '../bank-driver';

const LIJSTEN: Record<Soort, string> = { betaal: 'Betaalrekeningen', spaar: 'Spaarrekeningen' };

/** Het overzicht: per soort een lijst met rekeningen, elk met IBAN en saldo. */
export class OverzichtPagina {
  constructor(private readonly page: Page) {}

  async wachtTotGeladen(): Promise<void> {
    await expect(this.page.getByRole('heading', { name: /^Welkom/ })).toBeVisible();
  }

  async rekeningen(): Promise<RekeningInOverzicht[]> {
    const rekeningen: RekeningInOverzicht[] = [];
    for (const soort of Object.keys(LIJSTEN) as Soort[]) {
      const lijst = this.page.getByRole('list', { name: LIJSTEN[soort] });
      if ((await lijst.count()) === 0) continue;
      for (const regel of await lijst.getByRole('listitem').all()) {
        rekeningen.push({ soort, ...(await leesRegel(regel)) });
      }
    }
    return rekeningen;
  }
}

/** "NL12 SIMB 0123 4567 89" en "€ 1.842,17" terug naar domeinnotatie, zonder rekenen met floats. */
async function leesRegel(regel: Locator): Promise<{ iban: string; saldo: string }> {
  const tekst = (await regel.textContent()) ?? '';
  const iban = /NL\d{2}(?:\s?[A-Z0-9]{4}){3}\s?[A-Z0-9]{2}/.exec(tekst)?.[0].replace(/\s/g, '') ?? '';
  const bedrag = /(−|-)?\s?€\s?([\d.]+,\d{2})/.exec(tekst);
  const saldo = bedrag ? `${bedrag[1] ? '-' : ''}${bedrag[2].replace(/\./g, '').replace(',', '.')}` : '';
  return { iban, saldo };
}
