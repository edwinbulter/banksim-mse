import { expect, type Locator, type Page } from '@playwright/test';
import type { RekeninghouderInBeheer, Uitkomst } from '../bank-driver';

/** S10.1 (FO "Admin scherm"): de tabel met rekeninghouders en het zoekveld. */
export class BeheerPagina {
  constructor(private readonly page: Page) {}

  /** Via het adres, zoals een gebruiker het intypt. Gelukt: het beheerscherm; geweigerd: het eigen overzicht. */
  async open(): Promise<Uitkomst> {
    await this.page.goto('/beheer');
    const beheer = this.page.getByRole('heading', { name: 'Beheer' });
    const overzicht = this.page.getByRole('heading', { name: /^Welkom/ });
    await expect(beheer.or(overzicht)).toBeVisible();
    return (await beheer.isVisible()) ? 'gelukt' : 'geweigerd';
  }

  /** S10.2 (FO): de datumkiezer en "Toepassen"; gelukt als het scherm het toepassen meldt, geweigerd bij een melding. */
  async zetSimulatiedatum(datum: string): Promise<Uitkomst> {
    await this.page.goto('/beheer');
    await this.page.getByLabel('Simulatiedatum', { exact: true }).fill(datum);
    await this.page.getByRole('button', { name: 'Toepassen' }).click();
    const toegepast = this.page.getByRole('status').filter({ hasText: 'Simulatiedatum toegepast.' });
    const melding = this.page.getByRole('alert');
    await expect(toegepast.or(melding)).toBeVisible();
    return (await toegepast.isVisible()) ? 'gelukt' : 'geweigerd';
  }

  /** S10.2 (FO): "Terug naar vandaag"; gelukt als het scherm het meldt, geweigerd bij een melding. */
  async terugNaarVandaag(): Promise<Uitkomst> {
    await this.page.goto('/beheer');
    await this.page.getByRole('button', { name: 'Terug naar vandaag' }).click();
    const terug = this.page.getByRole('status').filter({ hasText: 'De bank staat weer op vandaag.' });
    const melding = this.page.getByRole('alert');
    await expect(terug.or(melding)).toBeVisible();
    return (await terug.isVisible()) ? 'gelukt' : 'geweigerd';
  }

  /** De datum in de datumkiezer, zodra het scherm hem geladen heeft. */
  async simulatiedatum(): Promise<string> {
    await this.page.goto('/beheer');
    const veld = this.page.getByLabel('Simulatiedatum', { exact: true });
    await expect(veld).not.toHaveValue('');
    return veld.inputValue();
  }

  /** Zoeken op het rekeningnummer en in de tabel op de betaalrekening klikken, zoals de beheerder dat doet. */
  async openRekening(iban: string): Promise<void> {
    await this.zoek(iban);
    await this.page.getByRole('table', { name: 'Rekeninghouders' }).getByRole('link', { name: iban }).click();
    await expect(this.page.getByRole('heading', { name: 'Betaalrekening' })).toBeVisible();
  }

  /** Zoeken op het rekeningnummer en in de tabel op de spaarrekening klikken. */
  async openSpaarrekening(iban: string): Promise<void> {
    await this.zoek(iban);
    await this.page.getByRole('table', { name: 'Rekeninghouders' }).getByRole('link', { name: iban }).click();
    await expect(this.page.getByRole('heading', { name: 'Spaarrekening' })).toBeVisible();
  }

  async zoek(tekst: string): Promise<RekeninghouderInBeheer[]> {
    await this.page.goto('/beheer');
    await this.page.getByLabel('Naam of rekeningnummer').fill(tekst);
    await this.page.getByRole('button', { name: 'Zoeken' }).click();
    const tabel = this.page.getByRole('table', { name: 'Rekeninghouders' });
    await expect(tabel.getByRole('row').nth(1).or(this.page.getByText('Geen rekeninghouders gevonden.'))).toBeVisible();
    const rijen = await tabel.getByRole('row').all();
    return Promise.all(rijen.slice(1).map(leesRij));
  }
}

/** Een regel: naam, betaalrekening, saldo, spaarrekening, spaarsaldo; terug naar domeinnotatie. */
async function leesRij(rij: Locator): Promise<RekeninghouderInBeheer> {
  const [naam, betaalrekening, saldo, spaarrekening, spaarsaldo] = await rij.getByRole('cell').allTextContents();
  return {
    naam: naam.trim(),
    betaalrekening: zonderSpaties(betaalrekening),
    saldo: bedrag(saldo),
    spaarrekening: spaarrekening.trim() === '' ? null : zonderSpaties(spaarrekening),
    spaarsaldo: spaarsaldo.trim() === '' ? null : bedrag(spaarsaldo),
  };
}

function zonderSpaties(tekst: string): string {
  return tekst.replace(/\s/g, '');
}

/** "€ 1.842,17" of "−€ 5,00" terug naar "1842.17" of "-5.00", zonder te rekenen. */
function bedrag(tekst: string): string {
  const deel = /(−|-)?\s?€\s?([\d.]+,\d{2})/.exec(tekst);
  return deel ? `${deel[1] ? '-' : ''}${deel[2].replace(/\./g, '').replace(',', '.')}` : tekst.trim();
}
