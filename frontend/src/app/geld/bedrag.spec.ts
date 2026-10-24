import { bedragMetTeken, bedragUitInvoer, bedragVoorWeergave } from './bedrag';

/** ADR 0010: de API levert "1842.17"; de frontend zet dat om voor weergave, zonder te rekenen. */
describe('bedragVoorWeergave', () => {
  it.each([
    ['1842.17', '€ 1.842,17'],
    ['0.05', '€ 0,05'],
    ['1000000.00', '€ 1.000.000,00'],
    ['12405.63', '€ 12.405,63'],
    ['-0.50', '−€ 0,50'],
    ['-812.40', '−€ 812,40'],
  ])('%s wordt %s', (bedrag, weergave) => {
    expect(bedragVoorWeergave(bedrag)).toBe(weergave);
  });

  it('weigert een bedrag zonder precies 2 decimalen', () => {
    expect(() => bedragVoorWeergave('1842.1')).toThrow('1842.1');
    expect(() => bedragVoorWeergave('1e3')).toThrow();
  });
});

/** S2.1: in de transactielijst staat elk bedrag met teken en zonder euroteken; − is het echte minteken. */
describe('bedragMetTeken', () => {
  it.each([
    ['3215.00', '+3.215,00'],
    ['-812.40', '−812,40'],
    ['0.05', '+0,05'],
    ['-1000000.00', '−1.000.000,00'],
  ])('%s wordt %s', (bedrag, weergave) => {
    expect(bedragMetTeken(bedrag)).toBe(weergave);
  });

  it('weigert een bedrag zonder precies 2 decimalen', () => {
    expect(() => bedragMetTeken('12.5')).toThrow('12.5');
  });
});

/** S7.1: het bedrag zoals de klant het typt, met komma, naar de notatie van de API (ADR 0010); als tekst, zonder rekenen. */
describe('bedragUitInvoer', () => {
  it('zet de komma om in een punt', () => {
    expect(bedragUitInvoer('25,00')).toBe('25.00');
  });
});
