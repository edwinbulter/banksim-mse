import { type ComponentFixture, TestBed } from '@angular/core/testing';
import { Betaalrekening } from './betaalrekening';

/** S2.1: de transacties zoals de API ze levert (nieuw naar oud), gegroepeerd per datum. */
describe('Betaalrekening', () => {
  let fixture: ComponentFixture<Betaalrekening>;

  function toon(transacties: { datum: string; tegenpartij: string; bedrag: string }[], meer = false): HTMLElement {
    fixture = TestBed.createComponent(Betaalrekening);
    fixture.componentRef.setInput('transacties', transacties);
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
});
