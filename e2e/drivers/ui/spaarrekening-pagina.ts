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

  /**
   * S9.1 (FO "Overschrijven scherm"): "Inleggen" opent het scherm Overschrijven met Van en Naar al ingevuld. Het bedrag
   * zoals de klant het typt, met komma; na "Overschrijven" weer op de spaarrekening.
   */
  async legIn(bedrag: string): Promise<void> {
    await this.page.getByRole('link', { name: 'Inleggen' }).click();
    await expect(this.page.getByRole('heading', { name: 'Overschrijven' })).toBeVisible();
    await this.page.getByLabel('Bedrag (€)').fill(bedrag.replace('.', ','));
    await this.page.getByRole('button', { name: 'Overschrijven' }).click();
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

  /** Een dag heeft een kop en een lijst mutaties; een regel zonder lijst is een jaartalregel ("2025"). */
  async koppen(): Promise<string[]> {
    const lijst = this.page.getByRole('list', { name: 'Mutaties' });
    await expect(lijst).toBeVisible();
    const koppen: string[] = [];
    for (const regel of await lijst.locator(':scope > li').all()) {
      if ((await regel.getByRole('list').count()) === 0) {
        koppen.push(((await regel.textContent()) ?? '').trim());
      } else {
        koppen.push(datumUitKop((await regel.getByRole('heading').textContent()) ?? ''));
      }
    }
    return koppen;
  }

  async mutaties(): Promise<Spaarmutatie[]> {
    const lijst = this.page.getByRole('list', { name: 'Mutaties' });
    await expect(lijst).toBeVisible();
    const mutaties: Spaarmutatie[] = [];
    for (const dag of await lijst.locator(':scope > li').all()) {
      if ((await dag.getByRole('list').count()) === 0) continue; // jaartalregel
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
