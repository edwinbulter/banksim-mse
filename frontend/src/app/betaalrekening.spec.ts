import { type ComponentFixture, TestBed } from '@angular/core/testing';
import { Betaalrekening, type Transactie } from './betaalrekening';

/** Een transactie zoals de API hem levert; details die een test niet noemt, krijgen een standaardwaarde. */
function transactie(t: Pick<Transactie, 'datum' | 'tegenpartij' | 'bedrag'> & Partial<Transactie>): Transactie {
  return {
    type: 'ONLINE_BANKIEREN',
    tijdstip: `${t.datum}T12:00:00+02:00`,
    van: { naam: 'Jan', iban: 'NL94SIMB0000000001' },
    naar: { naam: t.tegenpartij, iban: null },
    ...t,
  };
}

/** S2.1: de transacties zoals de API ze levert (nieuw naar oud), gegroepeerd per datum. */
describe('Betaalrekening', () => {
  let fixture: ComponentFixture<Betaalrekening>;

  function toon(
    transacties: (Pick<Transactie, 'datum' | 'tegenpartij' | 'bedrag'> & Partial<Transactie>)[],
    meer = false,
  ): HTMLElement {
    fixture = TestBed.createComponent(Betaalrekening);
    fixture.componentRef.setInput('transacties', transacties.map(transactie));
    fixture.componentRef.setInput('meer', meer);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function toonMeerKnop(pagina: HTMLElement): HTMLButtonElement | undefined {
    return [...pagina.querySelectorAll('button')].find((knop) => knop.textContent?.trim() === 'Toon meer');
  }

  function dagen(pagina: HTMLElement): { kop: string | undefined; regels: (string | undefined)[] }[] {
    return [...(pagina.querySelector('ul[aria-label="Transacties"]')?.children ?? [])].map((dag) => ({
      kop: dag.querySelector('h3')?.textContent ?? undefined,
      regels: [...dag.querySelectorAll('li')].map((li) => li.textContent?.replace(/\s+/g, ' ').trim()),
    }));
  }

  it('zet transacties van dezelfde dag onder één datumkop, in de volgorde van de API', () => {
    const pagina = toon([
      { datum: '2026-10-02', tegenpartij: 'Bakkerij De Korst', bedrag: '-12.50' },
      { datum: '2026-10-02', tegenpartij: 'Werkgever B.V.', bedrag: '3215.00' },
      { datum: '2026-10-01', tegenpartij: 'Woningstichting Thuis', bedrag: '-812.40' },
    ]);
    expect(pagina.querySelector('h2')?.textContent).toBe('Betaalrekening');
    expect(dagen(pagina)).toEqual([
      { kop: 'Vrijdag 2 oktober 2026', regels: ['Bakkerij De Korst −12,50', 'Werkgever B.V. +3.215,00'] },
      { kop: 'Donderdag 1 oktober 2026', regels: ['Woningstichting Thuis −812,40'] },
    ]);
  });

  it('meldt zonder transacties dat er nog geen af- of bijschrijvingen zijn, zonder lege lijst', () => {
    const pagina = toon([]);
    expect(pagina.querySelector('h2')?.textContent).toBe('Betaalrekening');
    expect(pagina.querySelector('p')?.textContent).toBe('Nog geen af- of bijschrijvingen.');
    expect(pagina.querySelector('ul[aria-label="Transacties"]')).toBeNull();
  });

  it('toont met transacties geen melding dat er nog geen zijn', () => {
    const pagina = toon([{ datum: '2026-10-01', tegenpartij: 'Woningstichting Thuis', bedrag: '-812.40' }]);
    expect(pagina.textContent).not.toContain('Nog geen af- of bijschrijvingen.');
  });

  it('toont "Toon meer" als er nog oudere transacties zijn, en meldt een klik (S2.2)', () => {
    const pagina = toon([{ datum: '2026-10-01', tegenpartij: 'Woningstichting Thuis', bedrag: '-812.40' }], true);
    let gekozen = 0;
    fixture.componentInstance.toonMeer.subscribe(() => gekozen++);
    toonMeerKnop(pagina)?.click();
    expect(gekozen).toBe(1);
  });

  it('toont geen "Toon meer" aan het eind van de lijst', () => {
    const pagina = toon([{ datum: '2026-10-01', tegenpartij: 'Woningstichting Thuis', bedrag: '-812.40' }], false);
    expect(toonMeerKnop(pagina)).toBeUndefined();
  });

  function regel(pagina: HTMLElement, tegenpartij: string): HTMLButtonElement | undefined {
    return [...pagina.querySelectorAll<HTMLButtonElement>('ul[aria-label="Transacties"] button')].find((knop) =>
      knop.textContent?.trim().startsWith(tegenpartij),
    );
  }

  function details(pagina: HTMLElement, tegenpartij: string): Record<string, string> | undefined {
    const lijst = pagina.querySelector(`dl[aria-label="Details van ${tegenpartij}"]`);
    if (!lijst) return undefined;
    return Object.fromEntries(
      [...lijst.querySelectorAll('dt')].map((dt) => [
        dt.textContent?.trim() ?? '',
        dt.nextElementSibling?.textContent?.replace(/\s+/g, ' ').trim() ?? '',
      ]),
    );
  }

  const INCASSO = {
    datum: '2026-10-01',
    tegenpartij: 'Woningstichting Thuis',
    bedrag: '-812.40',
    type: 'INCASSO',
    tijdstip: '2026-10-01T06:02:00+02:00',
    van: { naam: 'Jan', iban: 'NL94SIMB0000000001' },
    naar: { naam: 'Woningstichting Thuis', iban: 'NL91ABNA0417164300' },
  };

  it('klapt een transactie uit met type, Van, Naar, datum transactie en uitgevoerd op (S4.1)', () => {
    const pagina = toon([INCASSO]);
    expect(regel(pagina, 'Woningstichting Thuis')?.getAttribute('aria-expanded')).toBe('false');
    expect(details(pagina, 'Woningstichting Thuis')).toBeUndefined();

    regel(pagina, 'Woningstichting Thuis')?.click();
    fixture.detectChanges();

    expect(regel(pagina, 'Woningstichting Thuis')?.getAttribute('aria-expanded')).toBe('true');
    expect(details(pagina, 'Woningstichting Thuis')).toEqual({
      Transactietype: 'Incasso',
      Van: 'Jan NL94SIMB0000000001',
      Naar: 'Woningstichting Thuis NL91ABNA0417164300',
      'Datum transactie': '1 oktober 2026 om 06:02',
      'Uitgevoerd op': '1 oktober 2026',
    });
  });

  /** FO: omschrijving en betalingskenmerk worden, indien aanwezig, ook getoond (S7.1). */
  it('toont omschrijving en betalingskenmerk in de details als ze er zijn (S7.1)', () => {
    const pagina = toon([{ ...INCASSO, omschrijving: 'Etentje', betalingskenmerk: 'ETEN-2026' }]);
    regel(pagina, 'Woningstichting Thuis')?.click();
    fixture.detectChanges();
    expect(details(pagina, 'Woningstichting Thuis')?.['Omschrijving']).toBe('Etentje');
    expect(details(pagina, 'Woningstichting Thuis')?.['Betalingskenmerk']).toBe('ETEN-2026');
  });

  it('toont bij Naar alleen de naam als er geen IBAN is (betaalautomaat, S4.1)', () => {
    const pagina = toon([
      {
        datum: '2026-10-03',
        tegenpartij: 'Albert Heijn',
        bedrag: '-63.48',
        type: 'BETAALAUTOMAAT',
        naar: { naam: 'Albert Heijn', iban: null },
      },
    ]);
    regel(pagina, 'Albert Heijn')?.click();
    fixture.detectChanges();
    expect(details(pagina, 'Albert Heijn')?.['Naar']).toBe('Albert Heijn');
  });

  it('klapt een uitgeklapte transactie met een tweede klik weer in', () => {
    const pagina = toon([INCASSO]);
    regel(pagina, 'Woningstichting Thuis')?.click();
    fixture.detectChanges();
    regel(pagina, 'Woningstichting Thuis')?.click();
    fixture.detectChanges();
    expect(regel(pagina, 'Woningstichting Thuis')?.getAttribute('aria-expanded')).toBe('false');
    expect(details(pagina, 'Woningstichting Thuis')).toBeUndefined();
  });
});
