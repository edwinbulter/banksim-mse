package nl.banksim.seed;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.util.Map;

import tools.jackson.databind.json.JsonMapper;

/**
 * Het commando {@code sessie} (ADR 0020): leest per regel één opdracht als JSON-array met de argumenten, bijvoorbeeld
 * {@code ["klant","--alias","jan-f4de79-1","--betaalrekening","1842.17"]}, en schrijft per opdracht precies één regel
 * met de prefix {@code BANKSIM-SEED }: het resultaat, of {@code {"fout":"…"}}. Een fout stopt de sessie niet; het
 * einde van de invoer wel. Zo betaalt alleen de eerste opdracht van een worker de opstart van pod en JVM.
 */
class SeedSessie {

    static final String PREFIX = "BANKSIM-SEED ";

    private final SeedCommando commando;
    private final JsonMapper json;
    private final BufferedReader in;
    private final PrintStream uit;

    SeedSessie(SeedCommando commando, JsonMapper json, BufferedReader in, PrintStream uit) {
        this.commando = commando;
        this.json = json;
        this.in = in;
        this.uit = uit;
    }

    void voerUit() {
        try {
            for (String regel = in.readLine(); regel != null; regel = in.readLine()) {
                if (!regel.isBlank()) {
                    uit.println(PREFIX + json.writeValueAsString(antwoord(regel)));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Object antwoord(String regel) {
        try {
            return commando.resultaat(json.readValue(regel, String[].class));
        } catch (RuntimeException e) {
            return Map.of("fout", String.valueOf(e.getMessage()));
        }
    }
}
