import { expect, type Page } from '@playwright/test';
import type { Zoekformulier, Zoekopdracht } from '../bank-driver';

/** De meldingen van het zoekformulier, terug naar het probleemtype van de API (S5.1). */
const WEIGERINGEN: Record<string, string> = {
  'Bedrag van mag niet groter zijn dan Bedrag t/m': 'ongeldig-bedragbereik',
};

const RICHTINGEN = { alle: 'Alle transacties', uitgaand: 'Uitgaande transacties', inkomend: 'Inkomende transacties' };

/** Het zoekformulier op de betaalrekening: openen met "Zoeken in transacties", sluiten met "Verberg zoeken". */
export class ZoekenPagina {
  constructor(private readonly page: Page) {}

  async open(): Promise<void> {
    if (!(await this.formulier().isVisible())) {
      await this.page.getByRole('button', { name: 'Zoeken in transacties' }).click();
    }
    await expect(this.formulier()).toBeVisible();
  }

  async knop(): Promise<string> {
    const knop = this.page.getByRole('button', { name: /^(Zoeken in transacties|Verberg zoeken)$/ });
    return ((await knop.textContent()) ?? '').trim();
  }

  /**
   * Vult de velden in zoals de klant (bedragen letterlijk, ook met komma) en wacht op het antwoord van de API op
   * déze zoekopdracht. Niet op elk antwoord van /transacties: het laden van de rekening kan nog onderweg zijn, en
   * dan zou de lijst na het lezen alsnog door de zoekresultaten vervangen worden. Bij een weigering ook op de melding.
   */
  async zoek(opdracht: Zoekopdracht): Promise<void> {
    await this.open();
    const formulier = this.formulier();
    await formulier.getByLabel('Naam of IBAN').fill(opdracht.tekst ?? '');
    await formulier.getByLabel('Bedrag van (€)').fill(opdracht.van ?? '');
    await formulier.getByLabel('Bedrag t/m (€)').fill(opdracht.tm ?? '');
    await formulier.getByLabel('Transactietype').selectOption(opdracht.type ?? '');
    await formulier.getByRole('radio', { name: RICHTINGEN[opdracht.richting ?? 'alle'] }).check();
    const velden = Object.keys(opdracht).filter((veld) => opdracht[veld as keyof Zoekopdracht]?.trim()).sort();
    const antwoord = this.page.waitForResponse((r) => {
      const url = new URL(r.url());
      return url.pathname.endsWith('/transacties') && [...url.searchParams.keys()].sort().join() === velden.join();
    });
    await formulier.getByRole('button', { name: 'Zoeken', exact: true }).click();
    // Bij een weigering (400) staat de melding er pas als Angular het antwoord verwerkt heeft; weigering() wacht niet.
    if ((await antwoord).status() === 400) await expect(formulier.getByRole('alert')).toBeVisible();
  }

  /** De melding onder het formulier, als probleemtype; undefined zonder melding. */
  async weigering(): Promise<string | undefined> {
    const melding = this.formulier().getByRole('alert');
    if (!(await melding.isVisible())) return undefined;
    const tekst = ((await melding.textContent()) ?? '').trim();
    return WEIGERINGEN[tekst] ?? `onbekende melding: ${tekst}`;
  }

  async wis(): Promise<void> {
    const antwoord = this.page.waitForResponse((r) => r.url().includes('/transacties'));
    await this.formulier().getByRole('button', { name: 'Wissen' }).click();
    await antwoord;
  }

  async inhoud(): Promise<Zoekformulier> {
    const formulier = this.formulier();
    let gekozen: Zoekformulier['richting'] = 'alle';
    for (const sleutel of Object.keys(RICHTINGEN) as (keyof typeof RICHTINGEN)[]) {
      if (await formulier.getByRole('radio', { name: RICHTINGEN[sleutel] }).isChecked()) gekozen = sleutel;
    }
    return {
      tekst: await formulier.getByLabel('Naam of IBAN').inputValue(),
      van: await formulier.getByLabel('Bedrag van (€)').inputValue(),
      tm: await formulier.getByLabel('Bedrag t/m (€)').inputValue(),
      type: await formulier.getByLabel('Transactietype').inputValue(),
      richting: gekozen,
    };
  }

  private formulier() {
    return this.page.getByRole('form', { name: 'Zoeken in transacties' });
  }
}
