package nl.banksim.seed;

/** ADR 0040: de logins van de demo-data, met de wachtwoorden uit het Secret bank-demo. Bestaat de login al, dan blijft hij staan. */
public interface DemoLogins {

    void zorgVoorKlant(String gebruikersnaam, String wachtwoord);

    /** Een beheerder heeft ook de realm-rol beheerder (ADR 0015), ook als de vorige poging daarvoor afbrak. */
    void zorgVoorBeheerder(String gebruikersnaam, String wachtwoord);
}
