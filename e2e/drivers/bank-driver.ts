/** Soort rekening, zoals de DSL en de API hem noemen. */
export type Soort = 'betaal' | 'spaar';

/** Een rekening zoals het overzicht hem toont; het saldo in domeinnotatie ("1842.17"). */
export interface RekeningInOverzicht {
  soort: Soort;
  iban: string;
  saldo: string;
}

/** Een transactie zoals de klant hem ziet; het bedrag met teken in domeinnotatie ("-812.40"). */
export interface Transactie {
  tegenpartij: string;
  bedrag: string;
}

/** De transacties van één dag ("2026-10-02"), in de volgorde waarin het systeem ze toont. */
export interface TransactiesOpDatum {
  datum: string;
  transacties: Transactie[];
}

/** Wat de DSL van het systeem nodig heeft. De REST- en de UI-driver implementeren dit elk op hun eigen manier. */
export interface BankDriver {
  openStartpagina(): Promise<void>;
  /** De versie die BankSim toont of teruggeeft. */
  versie(): Promise<string>;
  /** Inloggen via Keycloak en de BFF, zoals een gebruiker dat doet. */
  logIn(gebruikersnaam: string, wachtwoord: string): Promise<void>;
  openOverzicht(): Promise<void>;
  rekeningenInOverzicht(): Promise<RekeningInOverzicht[]>;
  /** Vanuit het overzicht de rekening met dit IBAN openen. */
  openRekening(iban: string): Promise<void>;
  /** De transacties van de geopende rekening, per datum, in de volgorde van het systeem. */
  transacties(): Promise<TransactiesOpDatum[]>;
}
