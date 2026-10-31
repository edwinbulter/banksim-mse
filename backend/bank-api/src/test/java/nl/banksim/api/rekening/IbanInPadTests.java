package nl.banksim.api.rekening;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import nl.banksim.domain.rekening.Iban;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** ADR 0023: een ongeldige IBAN in het pad is een rekening die niet bestaat, dus 404 en geen 400. */
class IbanInPadTests {

    @Test
    void eenGeldigeIban() {
        assertThat(IbanInPad.van("NL94SIMB0000000001")).isEqualTo(Iban.van("NL94SIMB0000000001"));
    }

    @Test
    void eenOngeldigeIbanIs404() {
        assertThatThrownBy(() -> IbanInPad.van("geen-iban"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        fout -> assertThat(fout.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
