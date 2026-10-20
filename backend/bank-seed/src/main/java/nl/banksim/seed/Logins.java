package nl.banksim.seed;

/** Logins in de identiteitsprovider (ADR 0007). */
public interface Logins {

    /** Maakt een login aan en geeft het gegenereerde wachtwoord terug. */
    String maakLogin(String gebruikersnaam);
}
