package nl.banksim.seed;

import java.io.PrintStream;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;
import tools.jackson.databind.json.JsonMapper;

/**
 * De opdrachtregel van bank-seed, bijvoorbeeld {@code klant --alias jan-f4de79-1 --betaalrekening 1842.17} of
 * {@code transactie --iban NL94SIMB0000000001 --datum 2026-10-01 --bedrag -812.40 --tegenpartij "Woningstichting Thuis"}. Het
 * resultaat komt als één JSON-regel op stdout; de shell-driver leest de laatste regel (ADR 0003).
 */
class SeedCommando {

    private final KlantAanmaker klanten;
    private final Transacties transacties;
    private final JsonMapper json;
    private final PrintStream uit;

    SeedCommando(KlantAanmaker klanten, Transacties transacties, JsonMapper json, PrintStream uit) {
        this.klanten = klanten;
        this.transacties = transacties;
        this.json = json;
        this.uit = uit;
    }

    void voerUit(String... args) {
        String commando = args.length == 0 ? "" : args[0];
        Map<String, String> opties = opties(args);
        Object resultaat = switch (commando) {
            case "klant" -> klant(opties);
            case "transactie" -> transactie(opties);
            default -> throw new IllegalArgumentException(
                    "Onbekend commando: " + String.join(" ", args) + " (verwacht: klant of transactie)");
        };
        uit.println(json.writeValueAsString(resultaat));
    }

    private GeseedeKlant klant(Map<String, String> opties) {
        String alias = verplicht(opties, "--alias");
        Bedrag saldo = Bedrag.van(verplicht(opties, "--betaalrekening"));
        return klanten.klantMetBetaalrekening(alias, saldo);
    }

    private GeboekteTransactie transactie(Map<String, String> opties) {
        Iban iban = Iban.van(verplicht(opties, "--iban"));
        LocalDate datum = LocalDate.parse(verplicht(opties, "--datum"));
        Bedrag bedrag = Bedrag.van(verplicht(opties, "--bedrag"));
        String tegenpartij = verplicht(opties, "--tegenpartij");
        transacties.boek(iban, datum, bedrag, tegenpartij);
        return new GeboekteTransactie(iban.toString(), datum.toString(), bedrag.toString(), tegenpartij);
    }

    /** Het resultaat van {@code transactie}: wat er geboekt is. */
    record GeboekteTransactie(String iban, String datum, String bedrag, String tegenpartij) {
    }

    private static Map<String, String> opties(String[] args) {
        Map<String, String> opties = new HashMap<>();
        for (int i = 1; i + 1 < args.length; i += 2) {
            opties.put(args[i], args[i + 1]);
        }
        return opties;
    }

    private static String verplicht(Map<String, String> opties, String naam) {
        String waarde = opties.get(naam);
        if (waarde == null) {
            throw new IllegalArgumentException("Optie " + naam + " ontbreekt");
        }
        return waarde;
    }
}
