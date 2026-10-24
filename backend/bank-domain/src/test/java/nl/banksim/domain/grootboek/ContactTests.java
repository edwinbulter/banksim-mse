package nl.banksim.domain.grootboek;

import static org.assertj.core.api.Assertions.assertThat;

import nl.banksim.domain.rekening.Iban;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** S7.1 (FO): de naam van de ontvanger moet bij het contact met dit IBAN passen. */
class ContactTests {

    static final Contact SANNE = new Contact(Iban.simBank(2L), "Sanne de Vries");

    /** Hoofdletters en extra spaties tellen niet mee, zoals in het origineel. */
    @ParameterizedTest
    @ValueSource(strings = {"Sanne de Vries", "sanne de vries", "  Sanne   de Vries ", "SANNE DE VRIES"})
    void deNaamPastOokMetAndereHoofdlettersOfExtraSpaties(String opgegeven) {
        assertThat(SANNE.naamPast(opgegeven)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Piet", "Sanne", "Sanne de Vries B.V.", "SannedeVries", ""})
    void eenAndereNaamPastNiet(String opgegeven) {
        assertThat(SANNE.naamPast(opgegeven)).isFalse();
    }

    @Test
    void zonderNaamPastHetNiet() {
        assertThat(SANNE.naamPast(null)).isFalse();
    }
}
