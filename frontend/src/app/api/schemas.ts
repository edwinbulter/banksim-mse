import type { components } from './bank-api';

/**
 * De vormen van bank-api, uit het contract `api/bank-api.yaml` (ADR 0034). `bank-api.d.ts` wordt gegenereerd
 * (`npm --prefix api run types`); verander het contract, niet die types.
 */
type Schemas = components['schemas'];

export type Rekening = Schemas['Rekening'];
export type Pagina = Schemas['Pagina'];
export type Transactie = Schemas['Transactie'];
export type Partij = Schemas['Partij'];
export type LopendeRente = Schemas['LopendeRente'];
export type Contact = Schemas['Contact'];
export type Versie = Schemas['Versie'];
export type Betaling = Schemas['Betaling'];
export type Overschrijving = Schemas['Overschrijving'];
export type RekeninghouderInBeheer = Schemas['RekeninghouderInBeheer'];
export type Ik = Schemas['Ik'];
export type Simulatiedatum = Schemas['Simulatiedatum'];
