import { test } from '../../dsl/fixtures';

/** S11.1: een nieuwe installatie bevat de demo-data, zodat BankSim-MSE zonder voorbereiding te demonstreren is. */
test('Na installatie kunnen 10 klanten en een beheerder inloggen', async ({ omgeving }) => {
  await omgeving.als('de beheerder BankSim-MSE installeert', () => omgeving.installeer());
  await omgeving.dan('kan elk van de 10 klanten inloggen en ziet een betaalrekening en een spaarrekening', () =>
    omgeving.verwachtKlanten(10),
  );
  await omgeving.en('komt de beheerder na inloggen op het beheerscherm', () => omgeving.verwachtBeheerder());
});
