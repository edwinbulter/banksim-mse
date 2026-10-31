import { expect, type Page } from '@playwright/test';
import type { Betaalopdracht, MaximaleLengtes, Uitkomst } from '../bank-driver';

/** De meldingen na "Bevestigen" (S7.1) en "Overschrijven" (S9.1), terug naar het probleemtype van de API. */
export const WEIGERINGEN: Record<string, string> = {
  'Naar dit rekeningnummer kan niet worden betaald.': 'onbekend-contact',
  'De naam van de ontvanger hoort niet bij dit rekeningnummer.': 'naam-past-niet',
  'Saldo ontoereikend: deze betaling zou het saldo onder € 0,00 brengen, nu of op een latere datum.': 'saldo-ontoereikend',
  'Het bedrag moet groter zijn dan € 0,00.': 'ongeldige-invoer',
};

/** Het scherm Betalen (FO "Betalen scherm"): het formulier, het controlescherm en terug naar de betaalrekening. */
export class BetalenPagina {
  constructor(private readonly page: Page) {}

  /**
   * Vanaf de geopende betaalrekening; het bedrag zoals de klant het typt, met komma ("25,00"). Gelukt: terug op de
   * betaalrekening. Geweigerd: een melding op het controlescherm.
   */
  async betaal(opdracht: Betaalopdracht): Promise<Uitkomst> {
    await this.vulInEnGaNaarControle(opdracht);
    await this.page.getByRole('button', { name: 'Bevestigen' }).click();
    return this.uitkomst();
  }

  /** Een dubbelklik op "Bevestigen", zoals een ongeduldige klant. */
  async betaalDubbel({ bedrag, naam, iban }: Betaalopdracht): Promise<Uitkomst> {
    await this.vulInEnGaNaarControle({ bedrag, naam, iban });
    await this.page.getByRole('button', { name: 'Bevestigen' }).dblclick();
    return this.uitkomst();
  }

  /** Wachten tot de app klaar is: terug op de betaalrekening, of een melding (ADR 0031: wachten mag, beweren niet). */
  private async uitkomst(): Promise<Uitkomst> {
    await expect(this.page.getByRole('heading', { name: 'Betaalrekening' }).or(this.melding())).toBeVisible();
    return (await this.melding().isVisible()) ? 'geweigerd' : 'gelukt';
  }

  async open(): Promise<void> {
    await this.page.getByRole('link', { name: 'Betalen' }).click();
    await expect(this.page.getByRole('heading', { name: 'Betalen' })).toBeVisible();
  }

  /** Het attribuut maxlength van elk veld; zonder attribuut is het geen getal en faalt de vergelijking. */
  async maximaleLengtes(): Promise<MaximaleLengtes> {
    const lengte = async (label: string) =>
      Number(await this.page.getByLabel(label, { exact: true }).getAttribute('maxlength'));
    return {
      naamOntvanger: await lengte('Naam ontvanger'),
      rekeningnummer: await lengte('Rekeningnummer (IBAN)'),
      omschrijving: await lengte('Omschrijving'),
      betalingskenmerk: await lengte('Betalingskenmerk'),
      extraOmschrijving: await lengte('Extra omschrijving'),
    };
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

  /** De melding na "Bevestigen", terug naar het probleemtype van de API; undefined zonder melding. */
  async weigering(): Promise<string | undefined> {
    if (!(await this.melding().isVisible())) return undefined;
    const tekst = ((await this.melding().textContent()) ?? '').trim();
    return WEIGERINGEN[tekst] ?? `onbekende melding: ${tekst}`;
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
