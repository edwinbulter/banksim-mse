import { expect, type Page } from '@playwright/test';
import type { Partij, Transactie, TransactieDetails, TransactieType, TransactiesOpDatum } from '../bank-driver';

const MAANDEN = [
  'januari', 'februari', 'maart', 'april', 'mei', 'juni',
  'juli', 'augustus', 'september', 'oktober', 'november', 'december',
];

/** De labels van het scherm (FO), terug naar het type. */
const TYPEN: Record<string, TransactieType> = {
  'Online bankieren': 'ONLINE_BANKIEREN',
  'iDEAL | Wero': 'IDEAL_WERO',
  Betaalautomaat: 'BETAALAUTOMAAT',
  Incasso: 'INCASSO',
  Geldautomaat: 'GELDAUTOMAAT',
  Overschrijving: 'OVERSCHRIJVING',
  Verzamelbetaling: 'VERZAMELBETALING',
};

/**
 * De betaalrekening: per datum een kop ("Vrijdag 2 oktober 2026") met de transacties van die dag.
 * Zonder transacties staat er "Nog geen af- of bijschrijvingen."; dat is een lege lijst.
 */
export class BetaalrekeningPagina {
  private uitgeklapt?: string;

  constructor(private readonly page: Page) {}

  async open(iban: string): Promise<void> {
    await this.page.getByRole('list', { name: 'Betaalrekeningen' }).getByRole('button', { name: iban }).click();
    await expect(this.page.getByRole('heading', { name: 'Betaalrekening' })).toBeVisible();
  }

  async transacties(): Promise<TransactiesOpDatum[]> {
    const lijst = this.page.getByRole('list', { name: 'Transacties' });
    const leeg = this.page.getByText('Nog geen af- of bijschrijvingen.');
    await expect(lijst.or(leeg)).toBeVisible();
    if (await leeg.isVisible()) return [];
    const perDatum: TransactiesOpDatum[] = [];
    for (const dag of await lijst.locator(':scope > li').all()) {
      const datum = datumUitKop((await dag.getByRole('heading').textContent()) ?? '');
      const transacties: Transactie[] = [];
      // Alleen de regel zelf (de knop om uit te klappen), niet de details eronder (S4.1).
      for (const regel of await dag.getByRole('listitem').all()) {
        transacties.push(transactieUitRegel((await regel.getByRole('button').first().textContent()) ?? ''));
      }
      perDatum.push({ datum, transacties });
    }
    return perDatum;
  }

  /** Pas na transacties() betrouwbaar: dan is de lijst geladen en staat de knop er, of niet. */
  async heeftMeer(): Promise<boolean> {
    return this.toonMeerKnop().isVisible();
  }

  /** Klikt "Toon meer" en wacht tot er transactieregels bij zijn gekomen. */
  async toonMeer(): Promise<void> {
    const regels = this.page.getByRole('list', { name: 'Transacties' }).locator(':scope > li li');
    const voor = await regels.count();
    await this.toonMeerKnop().click();
    await expect.poll(() => regels.count(), 'transactieregels na "Toon meer"').toBeGreaterThan(voor);
  }

  /** Klikt de transactieregel die met deze tegenpartij begint; de details verschijnen eronder (S4.1). */
  async klapUit(tegenpartij: string): Promise<void> {
    const regel = this.page
      .getByRole('list', { name: 'Transacties' })
      .getByRole('button', { name: new RegExp(`^${escape(tegenpartij)}\\s`) });
    await regel.click();
    await expect(regel).toHaveAttribute('aria-expanded', 'true');
    this.uitgeklapt = tegenpartij;
  }

  /** Leest de details van de laatst uitgeklapte transactie terug in domeinnotatie. */
  async details(): Promise<TransactieDetails> {
    const details = this.page.getByLabel(`Details van ${this.uitgeklapt}`, { exact: true });
    await expect(details).toBeVisible();
    const waarde = async (term: string) =>
      ((await details.locator('dt', { hasText: term }).locator('+ dd').textContent()) ?? '').replace(/\s+/g, ' ').trim();
    const type = await waarde('Transactietype');
    if (!(type in TYPEN)) throw new Error(`Onbekend transactietype op het scherm: "${type}"`);
    return {
      transactietype: TYPEN[type],
      van: partij(await waarde('Van')),
      naar: partij(await waarde('Naar')),
      datumTransactie: tijdstipUitTekst(await waarde('Datum transactie')),
      uitgevoerdOp: datumUitTekst(await waarde('Uitgevoerd op')),
    };
  }

  /** De link "Overzicht" bovenaan, zoals "< Overzicht" in het origineel. */
  async kiesOverzicht(): Promise<void> {
    await this.page.getByRole('link', { name: 'Overzicht' }).click();
  }

  private toonMeerKnop() {
    return this.page.getByRole('button', { name: 'Toon meer' });
  }
}

/** "Vrijdag 2 oktober 2026" → "2026-10-02". */
function datumUitKop(kop: string): string {
  return datumUitTekst(kop.trim().replace(/^\S+\s+/, ''));
}

/** "2 oktober 2026" → "2026-10-02". */
function datumUitTekst(tekst: string): string {
  const delen = /^(\d{1,2})\s+(\S+)\s+(\d{4})$/.exec(tekst.trim());
  const maand = delen ? MAANDEN.indexOf(delen[2]) + 1 : 0;
  if (!delen || maand === 0) throw new Error(`Geen datum: "${tekst}"`);
  return `${delen[3]}-${String(maand).padStart(2, '0')}-${delen[1].padStart(2, '0')}`;
}

/** "1 oktober 2026 om 06:02" → "2026-10-01T06:02". */
function tijdstipUitTekst(tekst: string): string {
  const delen = /^(.*)\s+om\s+(\d{2}:\d{2})$/.exec(tekst);
  if (!delen) throw new Error(`Geen tijdstip: "${tekst}"`);
  return `${datumUitTekst(delen[1])}T${delen[2]}`;
}

/** "Woningstichting Thuis NL91ABNA0417164300" → naam en IBAN; zonder IBAN aan het eind is iban null. */
function partij(tekst: string): Partij {
  const delen = /^(.*\S)\s+(NL\d{2}[A-Z]{4}\d{10})$/.exec(tekst);
  return delen ? { naam: delen[1], iban: delen[2] } : { naam: tekst, iban: null };
}

function escape(tekst: string): string {
  return tekst.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/** "Werkgever B.V. +3.215,00" → { tegenpartij: "Werkgever B.V.", bedrag: "3215.00" }; − is het echte minteken. */
function transactieUitRegel(regel: string): Transactie {
  const delen = /^(.*\S)\s+([+−])([\d.]+,\d{2})$/.exec(regel.replace(/\s+/g, ' ').trim());
  if (!delen) throw new Error(`Geen transactieregel: "${regel}"`);
  const bedrag = delen[3].replace(/\./g, '').replace(',', '.');
  return { tegenpartij: delen[1], bedrag: `${delen[2] === '−' ? '-' : ''}${bedrag}` };
}
