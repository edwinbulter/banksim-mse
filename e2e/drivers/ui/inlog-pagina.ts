import { expect, type Page } from '@playwright/test';

/** Het inlogscherm van Keycloak (realm banksim-mse). Selecteert op de vaste id's van het Keycloak-thema. */
export class InlogPagina {
  constructor(private readonly page: Page) {}

  /** Wacht tot het inlogformulier er staat, of tot Keycloak al is teruggestuurd naar BankSim. */
  async vraagtWachtwoord(): Promise<boolean> {
    const wachtwoord = this.page.locator('#password');
    await expect(wachtwoord.or(this.page.getByRole('heading', { name: /^Welkom/ }))).toBeVisible();
    return wachtwoord.isVisible();
  }

  async logIn(gebruikersnaam: string, wachtwoord: string): Promise<void> {
    await this.probeer(gebruikersnaam, wachtwoord);
    await this.page.waitForURL(/^https:\/\/bank-mse\.localtest\.me\//);
  }

  async probeer(gebruikersnaam: string, wachtwoord: string): Promise<void> {
    await this.page.locator('#username').fill(gebruikersnaam);
    await this.page.locator('#password').fill(wachtwoord);
    await this.page.locator('#kc-login').click();
  }

  /** Wacht tot Keycloak een melding toont of BankSim geladen is; de melding staat in .kc-feedback-text. */
  async fout(): Promise<string | undefined> {
    const melding = this.page.locator('.kc-feedback-text').first();
    await expect(melding.or(this.page.getByRole('heading', { name: /^Welkom/ }))).toBeVisible();
    if (!(await melding.isVisible())) return undefined;
    return ((await melding.textContent()) ?? '').trim();
  }
}
