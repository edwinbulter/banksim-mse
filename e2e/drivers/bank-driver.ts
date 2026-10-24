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

/** De 7 transactietypen (FO); het scherm toont ze als "Incasso", "iDEAL | Wero" enzovoort. */
export type TransactieType =
  | 'ONLINE_BANKIEREN'
  | 'IDEAL_WERO'
  | 'BETAALAUTOMAAT'
  | 'INCASSO'
  | 'GELDAUTOMAAT'
  | 'OVERSCHRIJVING'
  | 'VERZAMELBETALING';

/** Een kant van de transactie: de naam en het IBAN, of null als er geen IBAN is (betaalautomaat). */
export interface Partij {
  naam: string;
  iban: string | null;
}

/**
 * De details van een uitgeklapte transactie (S4.1), in domeinnotatie: het tijdstip als "2026-10-01T06:02" en de
 * uitvoerdatum als "2026-10-01", allebei in Europe/Amsterdam.
 */
export interface TransactieDetails {
  transactietype: TransactieType;
  van: Partij;
  naar: Partij;
  datumTransactie: string;
  uitgevoerdOp: string;
}

/** Inkomend: bedrag > 0; uitgaand: bedrag < 0 (FO "Filteren op"). */
export type Richting = 'inkomend' | 'uitgaand';

/**
 * Een zoekopdracht zoals de klant hem invult (S5.1); lege velden doen niet mee, alle ingevulde moeten gelden. Bedragen
 * zijn de letterlijke invoer, ook met komma ("27,95"), en gelden voor de absolute waarde.
 */
export interface Zoekopdracht {
  tekst?: string;
  van?: string;
  tm?: string;
  type?: TransactieType;
  richting?: Richting;
}

/** Het zoekformulier zoals het op het scherm staat; type '' is "Alle transactietypes", richting 'alle' de standaard. */
export interface Zoekformulier {
  tekst: string;
  van: string;
  tm: string;
  type: string;
  richting: Richting | 'alle';
}

/** Een betaling zoals de klant hem invult (S7.1): het bedrag in domeinnotatie ("25.00"), de naam en het IBAN van de ontvanger. */
export interface Betaalopdracht {
  bedrag: string;
  naam: string;
  iban: string;
}

/** Of een verzoek dat iets verandert, is uitgevoerd of door de rechten geweigerd (403). */
export type Uitkomst = 'gelukt' | 'geweigerd';

/** Wat de DSL van het systeem nodig heeft. De REST- en de UI-driver implementeren dit elk op hun eigen manier. */
export interface BankDriver {
  openStartpagina(): Promise<void>;
  /** De versie die BankSim toont of teruggeeft. */
  versie(): Promise<string>;
  /** Inloggen via Keycloak en de BFF, zoals een gebruiker dat doet. */
  logIn(gebruikersnaam: string, wachtwoord: string): Promise<void>;
  /** Alleen in de UI-driver (@ui): inloggen zonder te verwachten dat het lukt (S6.1). */
  probeerInTeLoggen(gebruikersnaam: string, wachtwoord: string): Promise<void>;
  /** De melding op het inlogscherm, of undefined als BankSim al geladen is. */
  inlogfout(): Promise<string | undefined>;
  /** Uitloggen via "Uitloggen" (S6.2). */
  logUit(): Promise<void>;
  /** Of BankSim de rekeningen toont, of in plaats daarvan "Inloggen". */
  ingelogd(): Promise<boolean>;
  /** "Inloggen" kiezen: of Keycloak dan om gebruikersnaam en wachtwoord vraagt, in plaats van meteen terug te sturen. */
  inloggenVraagtWachtwoord(): Promise<boolean>;
  openOverzicht(): Promise<void>;
  rekeningenInOverzicht(): Promise<RekeningInOverzicht[]>;
  /** Vanuit het overzicht de rekening met dit IBAN openen. */
  openRekening(iban: string): Promise<void>;
  /** De rekening met dit IBAN openen via het adres, zonder het overzicht (S6.1). */
  openRekeningViaAdres(iban: string): Promise<void>;
  /** Of het systeem meldt dat de laatst geopende rekening niet bestaat (S6.1). */
  rekeningNietGevonden(): Promise<boolean>;
  /** De transacties van de geopende rekening die tot nu toe geladen zijn, per datum, in de volgorde van het systeem. */
  transacties(): Promise<TransactiesOpDatum[]>;
  /** Of er nog oudere transacties te laden zijn ("Toon meer"). */
  heeftMeer(): Promise<boolean>;
  /** De volgende portie oudere transacties laden. */
  toonMeer(): Promise<void>;
  /** De transactie met deze tegenpartij uitklappen (S4.1). */
  klapUit(tegenpartij: string): Promise<void>;
  /** De details van de transactie die het laatst is uitgeklapt. */
  details(): Promise<TransactieDetails>;
  /** Zoeken in de transacties van de geopende rekening; de lijst toont daarna wat gevonden is (S5.1). */
  zoek(opdracht: Zoekopdracht): Promise<void>;
  /** Het probleemtype ("ongeldig-bedragbereik") als de laatste zoekopdracht geweigerd werd, anders undefined. */
  weigering(): Promise<string | undefined>;
  /** Alleen in de UI-driver (@ui): het zoekformulier openen, de tekst van de knop, wissen en de inhoud lezen. */
  openZoeken(): Promise<void>;
  zoekknop(): Promise<string>;
  wisZoeken(): Promise<void>;
  zoekformulier(): Promise<Zoekformulier>;
  /** Vanaf een geopende rekening terug naar het overzicht, via de link "Overzicht". */
  naarOverzicht(): Promise<void>;
  /** De terugknop van de browser; alleen in de UI-driver (@ui). */
  terugInBrowser(): Promise<void>;
  /** Of de app sinds de laatste terugInBrowser opnieuw is geladen, in plaats van binnen de pagina terug te gaan. */
  opnieuwGeladen(): Promise<boolean>;
  /** De pagina herladen; alleen in de UI-driver (@ui). */
  herlaad(): Promise<void>;
  /** Vanaf de geopende rekening betalen en op het controlescherm bevestigen (S7.1); daarna staat de rekening weer open. */
  betaal(opdracht: Betaalopdracht): Promise<void>;
  /** De simulatiedatum ("2026-10-05") zetten met de eigen sessie; tot S10.2 via de API, ook in de UI-driver. */
  zetSimulatiedatum(datum: string): Promise<Uitkomst>;
  /** De simulatiedatum wissen: de bank draait weer op vandaag. */
  wisSimulatiedatum(): Promise<Uitkomst>;
}
