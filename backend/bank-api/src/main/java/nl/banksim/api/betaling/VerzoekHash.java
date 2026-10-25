package nl.banksim.api.betaling;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** ADR 0027: de velden van een verzoek als SHA-256 in hex; genoeg om een ander verzoek met dezelfde sleutel te herkennen. */
final class VerzoekHash {

    private VerzoekHash() {
    }

    static String van(String... velden) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(String.join("|", velden).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
