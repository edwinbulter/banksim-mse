package nl.banksim.api.betaling;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * ADR 0027: de velden van een verzoek als SHA-256 in hex; genoeg om een ander verzoek met dezelfde sleutel te herkennen.
 * Elk veld staat er met zijn lengte voor ("7:Etentje"), en een ontbrekend veld als "-". Zo vallen twee verschillende
 * verzoeken niet samen, ook niet als een veld een scheidingsteken of de tekst "null" bevat (backlog fase 7.1).
 */
final class VerzoekHash {

    private VerzoekHash() {
    }

    static String van(String... velden) {
        StringBuilder tekst = new StringBuilder();
        for (String veld : velden) {
            tekst.append(veld == null ? "-" : veld.length() + ":" + veld).append(';');
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(tekst.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
