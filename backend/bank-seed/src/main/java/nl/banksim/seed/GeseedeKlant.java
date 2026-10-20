package nl.banksim.seed;

/** Wat de DSL nodig heeft om als deze klant in te loggen en de rekening te herkennen (ADR 0003). */
public record GeseedeKlant(String gebruikersnaam, String wachtwoord, String iban) {
}
