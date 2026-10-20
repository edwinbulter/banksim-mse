package nl.banksim.domain.geld;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.BigRange;
import net.jqwik.api.constraints.Scale;
import org.junit.jupiter.api.Test;

/** ADR 0010: bedragen exact, met altijd 2 decimalen; als string met een punt, bijvoorbeeld "1842.17". */
class BedragTests {

    @Test
    void leestEnSchrijftDeStringnotatie() {
        assertThat(Bedrag.van("1842.17")).hasToString("1842.17");
        assertThat(Bedrag.van("-0.50")).hasToString("-0.50");
    }

    @Test
    void vultAanTotTweeDecimalen() {
        assertThat(Bedrag.van("10")).hasToString("10.00");
        assertThat(Bedrag.van("10.5")).isEqualTo(Bedrag.van("10.50"));
    }

    @Test
    void weigertMeerDanTweeDecimalenInPlaatsVanStilAfTeRonden() {
        assertThatThrownBy(() -> Bedrag.van("1.005"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1.005");
    }

    @Test
    void weigertGeenGetal() {
        assertThatThrownBy(() -> Bedrag.van("1.842,17")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Bedrag.van("1e3")).isInstanceOf(IllegalArgumentException.class);
    }

    @Property
    void heeftAltijdTweeDecimalenEnOverleeftDeStringnotatie(
            @ForAll @Scale(2) @BigRange(min = "-9999999999999.99", max = "9999999999999.99") BigDecimal waarde) {
        Bedrag bedrag = Bedrag.van(waarde.toPlainString());
        assertThat(bedrag.waarde().scale()).isEqualTo(2);
        assertThat(Bedrag.van(bedrag.toString())).isEqualTo(bedrag);
        assertThat(bedrag.waarde()).isEqualByComparingTo(waarde);
    }
}
