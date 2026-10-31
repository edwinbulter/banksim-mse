package nl.banksim.api.rekening;

import nl.banksim.domain.rekening.Iban;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** ADR 0023: een ongeldige IBAN in het pad is een rekening die niet bestaat, dus 404 en geen 400. */
public final class IbanInPad {

    private IbanInPad() {
    }

    public static Iban van(String tekst) {
        try {
            return Iban.van(tekst);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
}
