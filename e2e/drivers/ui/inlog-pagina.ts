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
    await this.page.locator('#username').fill(gebruikersnaam);
    await this.page.locator('#password').fill(wachtwoord);
    await this.page.locator('#kc-login').click();
    await this.page.waitForURL(/^https:\/\/bank-mse\.localtest\.me\//);
  }
}
