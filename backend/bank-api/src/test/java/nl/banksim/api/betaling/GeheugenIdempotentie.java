package nl.banksim.api.betaling;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** De bewaarde sleutels in het geheugen, voor tests zonder database (ADR 0027). */
class GeheugenIdempotentie implements Idempotentie {

    private final Map<String, String> hashes = new HashMap<>();

    void leeg() {
        hashes.clear();
    }

    @Override
    public Optional<String> claim(String sleutel, String gebruikersnaam, String verzoekHash) {
        return Optional.ofNullable(hashes.putIfAbsent(gebruikersnaam + " " + sleutel, verzoekHash));
    }
}
