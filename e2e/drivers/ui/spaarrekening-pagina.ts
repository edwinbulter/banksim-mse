import { expect, type Page } from '@playwright/test';
import type { Spaarmutatie } from '../bank-driver';
import { datumUitKop, transactieUitRegel } from './betaalrekening-pagina';

const TYPEN: Spaarmutatie['type'][] = ['Rente', 'Inleg', 'Opname'];

/**
 * Het spaarrekeningscherm (S8.1, FO): de mutaties per datum, nieuw naar oud. Een regel is de omschrijving en het
 * bedrag, bijvoorbeeld "Rente januari +25,48"; het eerste woord is het soort mutatie.
 */
export class SpaarrekeningPagina {
  constructor(private readonly page: Page) {}

  async open(iban: string): Promise<void> {
    await this.page.getByRole('list', { name: 'Spaarrekeningen' }).getByRole('button', { name: iban }).click();
    await expect(this.page.getByRole('heading', { name: 'Spaarrekening' })).toBeVisible();
  }

  /** In de kop: "Lopende rente € 12,36", terug naar domeinnotatie. */
  async lopendeRente(): Promise<string> {
    const waarde = this.page.locator('dt', { hasText: 'Lopende rente' }).locator('+ dd');
    await expect(waarde).toHaveText(/€/);
    const delen = /€\s?([\d.]+,\d{2})/.exec((await waarde.textContent()) ?? '');
    if (!delen) throw new Error(`Geen bedrag bij "Lopende rente": "${await waarde.textContent()}"`);
    return delen[1].replace(/\./g, '').replace(',', '.');
  }

  async mutaties(): Promise<Spaarmutatie[]> {
    const lijst = this.page.getByRole('list', { name: 'Mutaties' });
    await expect(lijst).toBeVisible();
    const mutaties: Spaarmutatie[] = [];
    for (const dag of await lijst.locator(':scope > li').all()) {
      const datum = datumUitKop((await dag.getByRole('heading').textContent()) ?? '');
      for (const regel of await dag.getByRole('listitem').all()) {
        const { tegenpartij: omschrijving, bedrag } = transactieUitRegel((await regel.textContent()) ?? '');
        const type = TYPEN.find((t) => omschrijving.split(' ')[0] === t);
        if (!type) throw new Error(`Onbekende spaarmutatie: "${omschrijving}"`);
        mutaties.push({ datum, type, bedrag });
      }
    }
    return mutaties;
  }
}
