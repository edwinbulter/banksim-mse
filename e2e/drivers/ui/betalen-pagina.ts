import { expect, type Page } from '@playwright/test';
import type { Betaalopdracht } from '../bank-driver';

/** Het scherm Betalen (FO "Betalen scherm"): het formulier, het controlescherm en terug naar de betaalrekening. */
export class BetalenPagina {
  constructor(private readonly page: Page) {}

  /** Vanaf de geopende betaalrekening; het bedrag zoals de klant het typt, met komma ("25,00"). */
  async betaal({ bedrag, naam, iban }: Betaalopdracht): Promise<void> {
    await this.page.getByRole('link', { name: 'Betalen' }).click();
    await this.page.getByLabel('Bedrag (€)').fill(bedrag.replace('.', ','));
    await this.page.getByLabel('Naam ontvanger').fill(naam);
    await this.page.getByLabel('Rekeningnummer (IBAN)').fill(iban);
    await this.page.getByRole('button', { name: 'Volgende' }).click();
    await expect(this.page.getByRole('heading', { name: 'Controleer je betaling' })).toBeVisible();
    await this.page.getByRole('button', { name: 'Bevestigen' }).click();
    await expect(this.page.getByRole('heading', { name: 'Betaalrekening' })).toBeVisible();
  }
}
