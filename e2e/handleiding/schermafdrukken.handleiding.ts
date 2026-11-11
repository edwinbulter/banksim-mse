import { expect, test, type Page } from '@playwright/test';
import { ShellDriver } from '../drivers/shell/shell-driver';

/**
 * Maakt de schermafdrukken van doc/banksim-mse-handleiding.md, met de demo-klant Jan de Vries en de beheerder (ADR 0040).
 * Alleen kijken: het script stopt vóór "Bevestigen" en "Overschrijven" en laat de simulatiedatum staan.
 */
const MAP = new URL('../../doc/handleiding/', import.meta.url).pathname;
const NAMESPACE = 'banksim-mse';

async function wachtwoord(sleutel: 'KLANT_WACHTWOORD' | 'BEHEERDER_WACHTWOORD'): Promise<string> {
  return new ShellDriver(process.env.BANKSIM_CONTEXT ?? 'kind-single-node').secretWaarde(NAMESPACE, 'bank-demo', sleutel);
}

async function bewaar(page: Page, naam: string): Promise<void> {
  // De muis weg van de laatste klik, anders staat die knop of regel in zijn hover-kleur op de afdruk.
  await page.mouse.move(0, 0);
  await page.screenshot({ path: `${MAP}${naam}.png` });
}

async function logIn(page: Page, gebruikersnaam: string, wachtwoord: string, schermafdruk?: string): Promise<void> {
  await page.goto('/');
  await page.getByRole('link', { name: 'Inloggen' }).click();
  await page.locator('#username').fill(gebruikersnaam);
  if (schermafdruk) await bewaar(page, schermafdruk);
  await page.locator('#password').fill(wachtwoord);
  await page.locator('#kc-login').click();
  await expect(page.getByRole('button', { name: 'Uitloggen' })).toBeVisible();
}

test('Schermafdrukken van de klant', async ({ page }) => {
  await logIn(page, 'jdevries', await wachtwoord('KLANT_WACHTWOORD'), '01-inloggen');

  await expect(page.getByRole('list', { name: 'Spaarrekeningen' })).toBeVisible();
  await bewaar(page, '02-overzicht');

  await page.getByRole('list', { name: 'Betaalrekeningen' }).getByRole('button').first().click();
  const transacties = page.getByRole('list', { name: 'Transacties' });
  await transacties.getByRole('button').first().click();
  await expect(page.getByRole('definition').first()).toBeVisible();
  await bewaar(page, '03-betaalrekening');

  await page.getByRole('button', { name: 'Zoeken in transacties' }).click();
  await page.getByLabel('Naam, bedrag, IBAN of omschrijving').fill('BuurtSuper');
  await page.getByRole('button', { name: 'Zoeken', exact: true }).click();
  await expect(transacties.getByRole('button').first()).toContainText('BuurtSuper');
  await bewaar(page, '04-zoeken');

  await page.getByRole('link', { name: 'Betalen' }).click();
  await page.getByLabel('Bedrag (€)').fill('25,00');
  await page.getByLabel('Naam ontvanger').pressSequentially('Lisa');
  const suggesties = page.getByRole('list', { name: 'Suggesties' });
  await expect(suggesties.getByRole('button', { name: /Lisa Jansen/ })).toBeVisible();
  await page.getByLabel('Omschrijving', { exact: true }).fill('Etentje');
  await bewaar(page, '05-betalen');

  await suggesties.getByRole('button', { name: /Lisa Jansen/ }).click();
  await page.getByRole('button', { name: 'Volgende' }).click();
  await expect(page.getByRole('heading', { name: 'Controleer je betaling' })).toBeVisible();
  await bewaar(page, '06-controle');

  await page.goto('/');
  await page.getByRole('list', { name: 'Spaarrekeningen' }).getByRole('button').first().click();
  await expect(page.getByRole('list', { name: 'Mutaties' })).toBeVisible();
  await bewaar(page, '07-spaarrekening');

  await page.getByRole('link', { name: 'Inleggen' }).click();
  // Van en Naar staan pas vast als de rekeningen geladen zijn; dan pas is "Overschrijven" te gebruiken.
  await expect(page.getByRole('region', { name: 'Van' })).toContainText(/NL\d{2}SIMB/);
  await page.getByLabel('Bedrag (€)').fill('100,00');
  await expect(page.getByRole('button', { name: 'Overschrijven' })).toBeEnabled();
  await bewaar(page, '08-overschrijven');
});

test('Schermafdrukken van de beheerder', async ({ page }) => {
  await logIn(page, 'beheerder', await wachtwoord('BEHEERDER_WACHTWOORD'));

  await expect(page.getByRole('heading', { name: 'Beheer' })).toBeVisible();
  await expect(page.getByText(/^Beschikbaar:/)).toBeVisible();
  await page.getByLabel('Naam of rekeningnummer').fill('Jansen');
  await page.getByRole('button', { name: 'Zoeken' }).click();
  const rekeninghouders = page.getByRole('table', { name: 'Rekeninghouders' });
  await expect(rekeninghouders).toBeVisible();
  await bewaar(page, '09-beheer');

  await rekeninghouders.getByRole('link').first().click();
  await expect(page.getByRole('list', { name: 'Transacties' })).toBeVisible();
  await bewaar(page, '10-alleen-lezen');
});
