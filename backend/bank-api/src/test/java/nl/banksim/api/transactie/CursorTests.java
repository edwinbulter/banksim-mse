package nl.banksim.api.transactie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;

import nl.banksim.domain.rekening.Iban;
import org.junit.jupiter.api.Test;

/** ADR 0011: de cursor is ondoorzichtig, ondertekend en hoort bij één rekening. */
class CursorTests {

    static final Iban VAN_JAN = Iban.simBank(1L);
    static final Positie POSITIE = new Positie(LocalDate.of(2026, 1, 17), 51L);

    private final Cursor cursor = new Cursor("test-sleutel-van-minstens-32-tekens");

    @Test
    void geeftDePositieTerugVoorDezelfdeRekening() {
        assertThat(cursor.lees(VAN_JAN, cursor.maak(VAN_JAN, POSITIE))).isEqualTo(POSITIE);
    }

    @Test
    void isUrlVeiligEnToontDePositieNietLetterlijk() {
        String tekst = cursor.maak(VAN_JAN, POSITIE);
        assertThat(tekst).matches("[A-Za-z0-9_.-]+").doesNotContain("2026-01-17");
    }

    @Test
    void weigertEenCursorVanEenAndereRekening() {
        String vanJan = cursor.maak(VAN_JAN, POSITIE);
        assertThatThrownBy(() -> cursor.lees(Iban.simBank(2L), vanJan)).isInstanceOf(OngeldigeCursor.class);
    }

    @Test
    void weigertEenAangepastePositieMetDeOudeHandtekening() {
        String echt = cursor.maak(VAN_JAN, POSITIE);
        String handtekening = echt.substring(echt.indexOf('.'));
        String anderePositie = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("2026-01-17,1".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> cursor.lees(VAN_JAN, anderePositie + handtekening)).isInstanceOf(OngeldigeCursor.class);
    }

    @Test
    void weigertEenCursorDieMetEenAndereSleutelIsGemaakt() {
        String metAndereSleutel = new Cursor("een-andere-sleutel-van-minstens-32-tekens").maak(VAN_JAN, POSITIE);
        assertThatThrownBy(() -> cursor.lees(VAN_JAN, metAndereSleutel)).isInstanceOf(OngeldigeCursor.class);
    }

    @Test
    void weigertOnleesbareTekst() {
        for (String onzin : new String[] {"", "geen-cursor", "a.b", "!!!.???", "." }) {
            assertThatThrownBy(() -> cursor.lees(VAN_JAN, onzin)).as(onzin).isInstanceOf(OngeldigeCursor.class);
        }
    }

    @Test
    void startNietZonderSleutelVanMinstens32Tekens() {
        assertThatThrownBy(() -> new Cursor("te-kort")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("banksim.cursor.sleutel");
        assertThatThrownBy(() -> new Cursor(null)).isInstanceOf(IllegalStateException.class);
    }
}
