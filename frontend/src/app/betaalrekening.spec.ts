import { TestBed } from '@angular/core/testing';
import { Betaalrekening } from './betaalrekening';

/** S2.1: de transacties zoals de API ze levert (nieuw naar oud), gegroepeerd per datum. */
describe('Betaalrekening', () => {
  function toon(transacties: { datum: string; tegenpartij: string; bedrag: string }[]): HTMLElement {
    const fixture = TestBed.createComponent(Betaalrekening);
    fixture.componentRef.setInput('transacties', transacties);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
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
});
