package nl.banksim.domain.grootboek;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.constraints.Size;
import nl.banksim.domain.geld.Bedrag;
import org.junit.jupiter.api.Test;

/** ADR 0025: een betaling mag het lopende saldo op de uitvoerdatum en op geen enkele latere dag onder € 0,00 brengen. */
class NooitRoodRegelTests {

    @Test
    void zonderLatereBewegingenTeltAlleenHetSaldoOpDeUitvoerdatum() {
        var verloop = new SaldoVerloop(Bedrag.van("500.00"), List.of());

        assertThat(NooitRoodRegel.staatToe(verloop, Bedrag.van("500.00"))).isTrue();
        assertThat(NooitRoodRegel.staatToe(verloop, Bedrag.van("500.01"))).isFalse();
    }

    /** Het scenario: € 500,00 op 5 oktober, € 450,00 incasso op 10 oktober; dan is € 50,00 het laagste saldo. */
    @Test
    void eenLatereAfschrijvingVerlaagtWatErBetaaldMagWorden() {
        var verloop = new SaldoVerloop(Bedrag.van("500.00"), List.of(Bedrag.van("-450.00")));

        assertThat(NooitRoodRegel.staatToe(verloop, Bedrag.van("50.00"))).isTrue();
        assertThat(NooitRoodRegel.staatToe(verloop, Bedrag.van("100.00"))).isFalse();
    }

    /** Een dip tussendoor telt mee, ook als het saldo daarna weer stijgt (optie 2 van ADR 0025 mist dit). */
    @Test
    void eenDipTussenTweeLatereBewegingenTeltMee() {
        var verloop = new SaldoVerloop(Bedrag.van("500.00"), List.of(Bedrag.van("-450.00"), Bedrag.van("3000.00")));

        assertThat(NooitRoodRegel.staatToe(verloop, Bedrag.van("50.01"))).isFalse();
    }

    @Test
    void eenLatereBijschrijvingMaaktHetSaldoOpDeUitvoerdatumNietRuimer() {
        var verloop = new SaldoVerloop(Bedrag.van("100.00"), List.of(Bedrag.van("1000.00")));

        assertThat(NooitRoodRegel.staatToe(verloop, Bedrag.van("100.01"))).isFalse();
    }

    /** Toegestaan betekent: na de betaling is elk lopend saldo minstens € 0,00; geweigerd: één zou eronder komen. */
    @Property
    void eenToegestaneBetalingLaatNooitEenLopendSaldoOnderNul(
            @ForAll @LongRange(min = 0, max = 1_000_000) long saldoInCenten,
            @ForAll @Size(max = 10) List<@LongRange(min = -500_000, max = 500_000) Long> bewegingenInCenten,
            @ForAll @LongRange(min = 1, max = 1_000_000) long bedragInCenten) {
        var verloop = new SaldoVerloop(centen(saldoInCenten), bewegingenInCenten.stream().map(NooitRoodRegelTests::centen).toList());
        BigDecimal bedrag = BigDecimal.valueOf(bedragInCenten, 2);

        BigDecimal lopend = BigDecimal.valueOf(saldoInCenten, 2).subtract(bedrag);
        boolean ooitOnderNul = lopend.signum() < 0;
        for (long beweging : bewegingenInCenten) {
            lopend = lopend.add(BigDecimal.valueOf(beweging, 2));
            ooitOnderNul |= lopend.signum() < 0;
        }
        assertThat(NooitRoodRegel.staatToe(verloop, new Bedrag(bedrag))).isEqualTo(!ooitOnderNul);
    }

    private static Bedrag centen(long centen) {
        return new Bedrag(BigDecimal.valueOf(centen, 2));
    }
}
