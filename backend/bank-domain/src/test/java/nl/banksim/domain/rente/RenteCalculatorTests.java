package nl.banksim.domain.rente;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.constraints.Size;
import nl.banksim.domain.geld.Bedrag;
import org.junit.jupiter.api.Test;

/** ADR 0028 (FO): 3% per jaar, per dag over het eindsaldo, op 10 decimalen; afronden bij het bijschrijven. */
class RenteCalculatorTests {

    /** € 10.000,00 × 0,03 / 365 = € 0,8219178082 per dag; × 31 = € 25,4794520542, afgerond € 25,48. */
    @Test
    void januari2026MetTienduizendEuroGeeft2548() {
        assertThat(RenteCalculator.rente(dagen(LocalDate.of(2026, 1, 1), 31, "10000.00"))).isEqualTo(Bedrag.van("25.48"));
    }

    /** 2028 is een schrikkeljaar: 366 dagen, dus € 0,8196721311 per dag; × 29 dagen in februari = € 23,77. */
    @Test
    void eenSchrikkeljaarRekentMet366Dagen() {
        assertThat(RenteCalculator.rente(dagen(LocalDate.of(2028, 2, 1), 29, "10000.00"))).isEqualTo(Bedrag.van("23.77"));
    }

    /** 15 dagen € 10.000,00 en 16 dagen € 20.000,00: € 12,33 + € 26,30 = € 38,63. */
    @Test
    void eenSaldoDatHalverwegeDeMaandVerandertTeltPerDag() {
        var dagen = new java.util.ArrayList<>(dagen(LocalDate.of(2026, 1, 1), 15, "10000.00"));
        dagen.addAll(dagen(LocalDate.of(2026, 1, 16), 16, "20000.00"));

        assertThat(RenteCalculator.rente(dagen)).isEqualTo(Bedrag.van("38.63"));
    }

    @Test
    void zonderDagenIsDeRenteNul() {
        assertThat(RenteCalculator.rente(List.of())).isEqualTo(Bedrag.van("0.00"));
    }

    /** Rente is nooit negatief: een negatief saldo levert geen rente op (en kost ook niets). */
    @Test
    void eenNegatiefSaldoLevertGeenRenteOp() {
        assertThat(RenteCalculator.rente(dagen(LocalDate.of(2026, 1, 1), 31, "-500.00"))).isEqualTo(Bedrag.van("0.00"));
    }

    @Property
    void renteIsNooitNegatief(@ForAll @Size(max = 31) List<@LongRange(min = -1_000_000_00L, max = 1_000_000_00L) Long> centen) {
        assertThat(RenteCalculator.rente(uitCenten(centen)).waarde()).isNotNegative();
    }

    /** Monotoon: een hoger saldo op elke dag geeft minstens zoveel rente. */
    @Property
    void renteStijgtMetHetSaldo(@ForAll @Size(min = 1, max = 31) List<@LongRange(min = 0, max = 1_000_000_00L) Long> centen,
            @ForAll @IntRange(min = 0, max = 1_000_000) int extraCenten) {
        var hoger = centen.stream().map(c -> c + extraCenten).toList();

        assertThat(RenteCalculator.rente(uitCenten(hoger)).waarde())
                .isGreaterThanOrEqualTo(RenteCalculator.rente(uitCenten(centen)).waarde());
    }

    private static List<DagSaldo> dagen(LocalDate eerste, int aantal, String saldo) {
        return IntStream.range(0, aantal).mapToObj(i -> new DagSaldo(eerste.plusDays(i), Bedrag.van(saldo))).toList();
    }

    private static List<DagSaldo> uitCenten(List<Long> centen) {
        LocalDate eerste = LocalDate.of(2026, 1, 1);
        return IntStream.range(0, centen.size())
                .mapToObj(i -> new DagSaldo(eerste.plusDays(i), new Bedrag(BigDecimal.valueOf(centen.get(i), 2)))).toList();
    }
}
