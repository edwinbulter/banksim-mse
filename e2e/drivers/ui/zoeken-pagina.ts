import { expect, type Locator, type Page, type Route } from '@playwright/test';
import type { Zoekformulier, Zoekopdracht } from '../bank-driver';
import { LEEG, transactieregels } from './betaalrekening-pagina';

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
    await formulier.getByLabel('Naam, bedrag, IBAN of omschrijving').fill(opdracht.tekst ?? '');
    await formulier.getByLabel('Bedrag van (€)').fill(opdracht.van ?? '');
    await formulier.getByLabel('Bedrag t/m (€)').fill(opdracht.tm ?? '');
    await formulier.getByLabel('Transactietype').selectOption(opdracht.type ?? '');
    await formulier.getByRole('radio', { name: RICHTINGEN[opdracht.richting ?? 'alle'] }).check();
    const velden = Object.keys(opdracht).filter((veld) => opdracht[veld as keyof Zoekopdracht]?.trim()).sort();
    const knop = formulier.getByRole('button', { name: 'Zoeken', exact: true });
    const antwoord = await this.klikEnTel(knop, (url) => [...url.searchParams.keys()].sort().join() === velden.join());
    // Bij een weigering (400) staat de melding er pas als Angular het antwoord verwerkt heeft; weigering() wacht niet.
    if (antwoord.status === 400) await expect(formulier.getByRole('alert')).toBeVisible();
    else await this.wachtOpLijst(antwoord);
  }

  /** De melding onder het formulier, als probleemtype; undefined zonder melding. */
  async weigering(): Promise<string | undefined> {
    const melding = this.formulier().getByRole('alert');
    if (!(await melding.isVisible())) return undefined;
    const tekst = ((await melding.textContent()) ?? '').trim();
    return WEIGERINGEN[tekst] ?? `onbekende melding: ${tekst}`;
  }

  async wis(): Promise<void> {
    await this.wachtOpLijst(await this.klikEnTel(this.formulier().getByRole('button', { name: 'Wissen' }), () => true));
  }

  async inhoud(): Promise<Zoekformulier> {
    const formulier = this.formulier();
    let gekozen: Zoekformulier['richting'] = 'alle';
    for (const sleutel of Object.keys(RICHTINGEN) as (keyof typeof RICHTINGEN)[]) {
      if (await formulier.getByRole('radio', { name: RICHTINGEN[sleutel] }).isChecked()) gekozen = sleutel;
    }
    return {
      tekst: await formulier.getByLabel('Naam, bedrag, IBAN of omschrijving').inputValue(),
      van: await formulier.getByLabel('Bedrag van (€)').inputValue(),
      tm: await formulier.getByLabel('Bedrag t/m (€)').inputValue(),
      type: await formulier.getByLabel('Transactietype').inputValue(),
      richting: gekozen,
    };
  }

  /**
   * Klikt de knop en geeft de status en het aantal transacties van het antwoord van /transacties waar `hoort` bij
   * past. Het antwoord gaat ongewijzigd door naar de browser; de driver leest het onderweg, in Node. Via
   * response.json() kon dat niet: Chromium had de inhoud soms niet meer (run 43268689127, lokaal 8 van de 360).
   */
  private async klikEnTel(knop: Locator, hoort: (url: URL) => boolean): Promise<{ status: number; aantal: number }> {
    const isTransacties = (url: URL) => url.pathname.endsWith('/transacties');
    let gezien: (antwoord: { status: number; aantal: number }) => void = () => {};
    const binnen = new Promise<{ status: number; aantal: number }>((klaar) => (gezien = klaar));
    const onderweg = async (route: Route) => {
      if (!hoort(new URL(route.request().url()))) return route.fallback();
      const antwoord = await route.fetch();
      const aantal = antwoord.status() === 200 ? ((await antwoord.json()) as { transacties: unknown[] }).transacties.length : 0;
      await route.fulfill({ response: antwoord });
      gezien({ status: antwoord.status(), aantal });
    };
    await this.page.route(isTransacties, onderweg);
    try {
      await knop.click();
      return await binnen;
    } finally {
      await this.page.unroute(isTransacties, onderweg);
    }
  }

  /**
   * Het antwoord is binnen, maar Angular toont het pas daarna. Zonder te wachten leest transacties() de vorige lijst,
   * of een mengsel van de vorige en de nieuwe (run 43242264556). Daarom: wachten tot de lijst evenveel transactieregels
   * heeft als het antwoord, of tot de tekst voor een lege lijst er staat.
   */
  private async wachtOpLijst({ status, aantal }: { status: number; aantal: number }): Promise<void> {
    if (status !== 200) return;
    if (aantal === 0) await expect(this.page.getByText(LEEG)).toBeVisible();
    else await expect(transactieregels(this.page), 'transactieregels na het antwoord').toHaveCount(aantal);
  }

  private formulier() {
    return this.page.getByRole('form', { name: 'Zoeken in transacties' });
  }
}
