import { expect, type Page } from '@playwright/test';
import type { Betaalopdracht } from '../bank-driver';

/** De meldingen na "Bevestigen", terug naar het probleemtype van de API (S7.1). */
const WEIGERINGEN: Record<string, string> = {
  'Naar dit rekeningnummer kan niet worden betaald.': 'onbekend-contact',
  'De naam van de ontvanger hoort niet bij dit rekeningnummer.': 'naam-past-niet',
  'Saldo ontoereikend: deze betaling zou het saldo onder € 0,00 brengen, nu of op een latere datum.': 'saldo-ontoereikend',
};

/** Het scherm Betalen (FO "Betalen scherm"): het formulier, het controlescherm en terug naar de betaalrekening. */
export class BetalenPagina {
  constructor(private readonly page: Page) {}

  /** Vanaf de geopende betaalrekening; het bedrag zoals de klant het typt, met komma ("25,00"). */
  async betaal(opdracht: Betaalopdracht): Promise<void> {
    await this.bevestig(opdracht);
    await expect(this.page.getByRole('heading', { name: 'Betaalrekening' })).toBeVisible();
  }

  /** Een dubbelklik op "Bevestigen", zoals een ongeduldige klant; daarna terug op de betaalrekening. */
  async betaalDubbel({ bedrag, naam, iban }: Betaalopdracht): Promise<void> {
    await this.vulInEnGaNaarControle({ bedrag, naam, iban });
    await this.page.getByRole('button', { name: 'Bevestigen' }).dblclick();
    await expect(this.page.getByRole('heading', { name: 'Betaalrekening' })).toBeVisible();
  }

  /** Vanaf de geopende betaalrekening: typen in het IBAN-veld, en de suggestie met dit IBAN kiezen. */
  async kiesSuggestie(tekst: string, iban: string): Promise<void> {
    await this.page.getByRole('link', { name: 'Betalen' }).click();
    await this.page.getByLabel('Rekeningnummer (IBAN)').fill(tekst);
    const suggesties = this.page.getByRole('list', { name: 'Suggesties' });
    await suggesties.getByRole('button', { name: new RegExp(iban) }).click();
    // Angular vult de velden en sluit de lijst in dezelfde verwerking; pas daarna staan beide velden goed.
    await expect(suggesties).toBeHidden();
  }

  async ontvanger(): Promise<{ naam: string; iban: string }> {
    return {
      naam: await this.page.getByLabel('Naam ontvanger').inputValue(),
      iban: await this.page.getByLabel('Rekeningnummer (IBAN)').inputValue(),
    };
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

  private async bevestig(opdracht: Betaalopdracht): Promise<void> {
    await this.vulInEnGaNaarControle(opdracht);
    await this.page.getByRole('button', { name: 'Bevestigen' }).click();
  }

  private async vulInEnGaNaarControle({ bedrag, naam, iban, omschrijving, betalingskenmerk }: Betaalopdracht): Promise<void> {
    await this.page.getByRole('link', { name: 'Betalen' }).click();
    await this.page.getByLabel('Bedrag (€)').fill(bedrag.replace('.', ','));
    await this.page.getByLabel('Naam ontvanger').fill(naam);
    await this.page.getByLabel('Rekeningnummer (IBAN)').fill(iban);
    if (omschrijving) await this.page.getByLabel('Omschrijving', { exact: true }).fill(omschrijving);
    if (betalingskenmerk) await this.page.getByLabel('Betalingskenmerk').fill(betalingskenmerk);
    await this.page.getByRole('button', { name: 'Volgende' }).click();
    await expect(this.page.getByRole('heading', { name: 'Controleer je betaling' })).toBeVisible();
  }

  private melding() {
    return this.page.getByRole('alert');
  }
}
