package nl.banksim.domain.rekening;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.api.Test;

/** Nederlandse IBAN's van de gesimuleerde bank (bankcode SIMB) met het mod-97-controlegetal. */
class IbanTests {

    @Test
    void berekentHetControlegetalVoorEenRekeningnummerBijSimBank() {
        assertThat(Iban.simBank(123456789L)).hasToString("NL38SIMB0123456789");
        assertThat(Iban.simBank(1L)).hasToString("NL94SIMB0000000001");
    }

    @Test
    void accepteertEenGeldigeIbanVanEenAndereBank() {
        assertThat(Iban.van("NL91ABNA0417164300")).hasToString("NL91ABNA0417164300");
    }

    @Test
    void weigertEenFoutControlegetal() {
        assertThatThrownBy(() -> Iban.van("NL39SIMB0123456789"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NL39SIMB0123456789");
    }

    @Test
    void weigertEenRekeningnummerVanMeerDanTienCijfers() {
        assertThatThrownBy(() -> Iban.simBank(10_000_000_000L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Iban.simBank(-1L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Property
    void elkeIbanVanSimBankIsGeldig(@ForAll @LongRange(min = 0, max = 9_999_999_999L) long rekeningnummer) {
        Iban iban = Iban.simBank(rekeningnummer);
        assertThat(Iban.van(iban.toString())).isEqualTo(iban);
    }
}
