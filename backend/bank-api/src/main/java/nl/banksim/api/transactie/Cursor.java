package nl.banksim.api.transactie;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import nl.banksim.domain.rekening.Iban;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * De cursor voor "Toon meer" (ADR 0011, 0035): {@code base64url(datum,tijdstip,id) "."
 * base64url(HMAC-SHA256(iban,datum,tijdstip,id))}. Door
 * het IBAN in de handtekening hoort een cursor bij één rekening. De sleutel komt uit het Secret bank-api.
 */
@Component
class Cursor {

    private static final String ALGORITME = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final SecretKeySpec sleutel;

    Cursor(@Value("${banksim.cursor.sleutel:#{null}}") String sleutel) {
        if (sleutel == null || sleutel.length() < 32) {
            throw new IllegalStateException("banksim.cursor.sleutel ontbreekt of is korter dan 32 tekens (ADR 0011)");
        }
        this.sleutel = new SecretKeySpec(sleutel.getBytes(StandardCharsets.UTF_8), ALGORITME);
    }

    String maak(Iban iban, Positie positie) {
        String inhoud = positie.datum() + "," + positie.tijdstip() + "," + positie.id();
        return ENCODER.encodeToString(bytes(inhoud)) + "." + ENCODER.encodeToString(handtekening(iban, inhoud));
    }

    /**
     * Alleen een onleesbare cursor is een {@link OngeldigeCursor}: geen punt, geen base64, niet drie delen, geen datum,
     * geen tijdstip of geen id. Een fout in de HMAC-configuratie is een fout van de bank en geen 400 (backlog fase 3.5).
     */
    Positie lees(Iban iban, String cursor) {
        int punt = cursor.indexOf('.');
        // De mutant "< 0 wordt <= 0" is gelijkwaardig: bij een punt vooraan faalt daarna de handtekening.
        if (punt < 0) {
            throw new OngeldigeCursor();
        }
        try {
            String inhoud = new String(DECODER.decode(cursor.substring(0, punt)), StandardCharsets.UTF_8);
            byte[] gegeven = DECODER.decode(cursor.substring(punt + 1));
            if (!MessageDigest.isEqual(gegeven, handtekening(iban, inhoud))) {
                throw new OngeldigeCursor();
            }
            String[] delen = inhoud.split(",", 3);
            if (delen.length != 3) {
                throw new OngeldigeCursor();
            }
            return new Positie(LocalDate.parse(delen[0]), OffsetDateTime.parse(delen[1]), Long.parseLong(delen[2]));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            // Base64 en het id gooien een IllegalArgumentException, de datum en het tijdstip een DateTimeParseException.
            throw new OngeldigeCursor();
        }
    }

    private byte[] handtekening(Iban iban, String inhoud) {
        try {
            Mac mac = Mac.getInstance(ALGORITME);
            mac.init(sleutel);
            return mac.doFinal(bytes(iban + "," + inhoud));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] bytes(String tekst) {
        return tekst.getBytes(StandardCharsets.UTF_8);
    }
}
