package nl.banksim.seed;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

import nl.banksim.domain.geld.Bedrag;
import tools.jackson.databind.json.JsonMapper;

/**
 * De opdrachtregel van bank-seed, bijvoorbeeld {@code klant --alias jan-f4de79-1 --betaalrekening 1842.17}. Het
 * resultaat komt als één JSON-regel op stdout; de shell-driver leest de laatste regel (ADR 0003).
 */
class SeedCommando {

    private final KlantAanmaker klanten;
    private final JsonMapper json;
    private final PrintStream uit;

    SeedCommando(KlantAanmaker klanten, JsonMapper json, PrintStream uit) {
        this.klanten = klanten;
        this.json = json;
        this.uit = uit;
    }

    void voerUit(String... args) {
        if (args.length == 0 || !"klant".equals(args[0])) {
            throw new IllegalArgumentException("Onbekend commando: " + String.join(" ", args) + " (verwacht: klant)");
        }
        Map<String, String> opties = opties(args);
        String alias = verplicht(opties, "--alias");
        Bedrag saldo = Bedrag.van(verplicht(opties, "--betaalrekening"));
        uit.println(json.writeValueAsString(klanten.klantMetBetaalrekening(alias, saldo)));
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
