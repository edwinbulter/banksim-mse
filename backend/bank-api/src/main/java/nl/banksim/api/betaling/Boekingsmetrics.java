package nl.banksim.api.betaling;

import io.micrometer.core.instrument.Metrics;

/**
 * ADR 0054: de tellers van betalingen en overschrijvingen, in de globale registry van Micrometer; Spring Boot koppelt
 * daar zijn registry aan. De labels hebben weinig varianten: de soort en de reden van een weigering, nooit een klant,
 * IBAN of bedrag.
 */
final class Boekingsmetrics {

    static final String BETALING = "betaling";
    static final String OVERSCHRIJVING = "overschrijving";

    private Boekingsmetrics() {
    }

    static void afgehandeld(Afhandeling afhandeling, String soort) {
        String naam = switch (afhandeling) {
            case GEBOEKT -> "bank.boekingen";
            case HERHAALD -> "bank.idempotentie.herhaald";
        };
        Metrics.counter(naam, "soort", soort).increment();
    }

    static void geweigerd(Weigering reden, String soort) {
        Metrics.counter("bank.boekingen.geweigerd", "reden", reden.code(), "soort", soort).increment();
    }

    /** De soort volgt uit het pad: de weigeringen van beide endpoints komen in dezelfde advice. */
    static String soortVan(String pad) {
        return pad.startsWith("/api/overschrijvingen") ? OVERSCHRIJVING : BETALING;
    }
}
