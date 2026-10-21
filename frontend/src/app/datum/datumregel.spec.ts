import { datumregel } from './datumregel';

/** S2.1: de transactielijst groepeert per datum onder een kop als "Vrijdag 2 oktober 2026". */
describe('datumregel', () => {
  it.each([
    ['2026-10-02', 'Vrijdag 2 oktober 2026'],
    ['2026-10-01', 'Donderdag 1 oktober 2026'],
    ['2026-01-04', 'Zondag 4 januari 2026'],
    ['2024-02-29', 'Donderdag 29 februari 2024'],
  ])('%s wordt %s', (datum, regel) => {
    expect(datumregel(datum)).toBe(regel);
  });

  it('weigert iets anders dan een datum als "2026-10-02"', () => {
    expect(() => datumregel('2-10-2026')).toThrow('2-10-2026');
    expect(() => datumregel('2026-02-30')).toThrow('2026-02-30');
  });
});
