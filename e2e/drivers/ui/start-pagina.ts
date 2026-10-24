import { expect, type Page } from '@playwright/test';

export class StartPagina {
  constructor(private readonly page: Page) {}

  async open(): Promise<void> {
    await this.page.goto('/');
  }

  async kiesInloggen(): Promise<void> {
    await this.page.getByRole('link', { name: 'Inloggen' }).click();
  }

  /**
   * Klikt "Uitloggen" en wacht tot de app via Keycloak opnieuw geladen is (ADR 0021): een markering op window verdwijnt
   * pas met de oude pagina. Zonder die wachtstap ziet de volgende stap nog het oude overzicht.
   */
  async kiesUitloggen(): Promise<void> {
    await this.page.evaluate(() => Object.assign(window, { banksimVoorUitloggen: true }));
    await this.page.getByRole('button', { name: 'Uitloggen' }).click();
    await expect
      .poll(
        // Tijdens de navigatie verdwijnt de context van de pagina; dan is de oude pagina in elk geval weg.
        () => this.page.evaluate(() => 'banksimVoorUitloggen' in window).catch(() => true),
        { message: 'de app opnieuw geladen na "Uitloggen"' },
      )
      .toBe(false);
    await this.page.waitForLoadState();
  }

  /** Wacht tot BankSim het overzicht ("Welkom …") of de link "Inloggen" toont. */
  async ingelogd(): Promise<boolean> {
    const welkom = this.page.getByRole('heading', { name: /^Welkom/ });
    await expect(welkom.or(this.page.getByRole('link', { name: 'Inloggen' }))).toBeVisible();
    return welkom.isVisible();
  }

  /** De voettekst toont "Versie <versie>" zodra de API heeft geantwoord. */
  async versie(): Promise<string> {
    const voettekst = this.page.getByRole('contentinfo');
    await expect(voettekst).toContainText(/Versie \S+/);
    const tekst = (await voettekst.textContent()) ?? '';
    return tekst.replace(/.*Versie\s+/s, '').trim();
  }
}
