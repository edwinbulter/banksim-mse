import { expect, type Page } from '@playwright/test';
import type { Betaalopdracht } from '../bank-driver';

/** De meldingen na "Bevestigen", terug naar het probleemtype van de API (S7.1). */
const WEIGERINGEN: Record<string, string> = {
  'Naar dit rekeningnummer kan niet worden betaald.': 'onbekend-contact',
  'De naam van de ontvanger hoort niet bij dit rekeningnummer.': 'naam-past-niet',
  'Saldo ontoereikend: deze betaling zou het saldo onder € 0,00 brengen.': 'saldo-ontoereikend',
};

/** Het scherm Betalen (FO "Betalen scherm"): het formulier, het controlescherm en terug naar de betaalrekening. */
export class BetalenPagina {
  constructor(private readonly page: Page) {}

  /** Vanaf de geopende betaalrekening; het bedrag zoals de klant het typt, met komma ("25,00"). */
  async betaal(opdracht: Betaalopdracht): Promise<void> {
    await this.bevestig(opdracht);
    await expect(this.page.getByRole('heading', { name: 'Betaalrekening' })).toBeVisible();
  }

  /** Na "Bevestigen": terug op de betaalrekening, of een melding op het controlescherm. */
  async probeer(opdracht: Betaalopdracht): Promise<void> {
    await this.bevestig(opdracht);
    await expect(this.page.getByRole('heading', { name: 'Betaalrekening' }).or(this.melding())).toBeVisible();
  }

  /** De melding na "Bevestigen", terug naar het probleemtype van de API; undefined zonder melding. */
  async weigering(): Promise<string | undefined> {
    if (!(await this.melding().isVisible())) return undefined;
    const tekst = ((await this.melding().textContent()) ?? '').trim();
    return WEIGERINGEN[tekst] ?? `onbekende melding: ${tekst}`;
  }

  private async bevestig({ bedrag, naam, iban }: Betaalopdracht): Promise<void> {
    await this.page.getByRole('link', { name: 'Betalen' }).click();
    await this.page.getByLabel('Bedrag (€)').fill(bedrag.replace('.', ','));
    await this.page.getByLabel('Naam ontvanger').fill(naam);
    await this.page.getByLabel('Rekeningnummer (IBAN)').fill(iban);
    await this.page.getByRole('button', { name: 'Volgende' }).click();
    await expect(this.page.getByRole('heading', { name: 'Controleer je betaling' })).toBeVisible();
    await this.page.getByRole('button', { name: 'Bevestigen' }).click();
  }

  private melding() {
    return this.page.getByRole('alert');
  }
}
