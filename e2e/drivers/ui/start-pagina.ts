import { expect, type Page } from '@playwright/test';

export class StartPagina {
  constructor(private readonly page: Page) {}

  async open(): Promise<void> {
    await this.page.goto('/');
  }

  async kiesInloggen(): Promise<void> {
    await this.page.getByRole('link', { name: 'Inloggen' }).click();
  }

  /** De voettekst toont "Versie <versie>" zodra de API heeft geantwoord. */
  async versie(): Promise<string> {
    const voettekst = this.page.getByRole('contentinfo');
    await expect(voettekst).toContainText(/Versie \S+/);
    const tekst = (await voettekst.textContent()) ?? '';
    return tekst.replace(/.*Versie\s+/s, '').trim();
  }
}
