package nl.banksim.seed;

/** Beheerders in de identiteitsprovider: een login met de realm-rol beheerder (ADR 0015). */
public interface Beheerders {

    /** Maakt een beheerder aan en geeft het gegenereerde wachtwoord terug. */
    String maakBeheerder(String gebruikersnaam);
}
