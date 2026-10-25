package nl.banksim.domain.grootboek;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;
import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;
import nl.banksim.domain.transactie.TransactieType;
import org.junit.jupiter.api.Test;

/** ADR 0024: een overboeking heeft precies twee kanten, op twee rekeningen, die samen nul zijn. */
class OverboekingTests {

    static final Partij JAN = new Partij(Iban.simBank(1L), "Jan");
    static final Partij SANNE = new Partij(Iban.simBank(2L), "Sanne");
    static final LocalDateTime TIJDSTIP = LocalDateTime.of(2026, 10, 5, 14, 0);

    @Test
    void schrijftAfBijDeEneKantEnBijBijDeAndereMetElkaarAlsTegenpartij() {
        var overboeking = new Overboeking(JAN, SANNE, Bedrag.van("25.00"), TransactieType.ONLINE_BANKIEREN, TIJDSTIP);

        assertThat(overboeking.boekingen()).containsExactly(
                new Boeking(JAN.iban(), Bedrag.van("-25.00"), SANNE, TransactieType.ONLINE_BANKIEREN),
                new Boeking(SANNE.iban(), Bedrag.van("25.00"), JAN, TransactieType.ONLINE_BANKIEREN));
    }

    /** ADR 0029: een overschrijving heeft een type per kant; de overboeking zelf is een Overschrijving. */
    @Test
    void eenOverschrijvingGeeftElkeKantZijnEigenType() {
        var spaar = new Partij(Iban.simBank(3L), "Jan");
        var overboeking = Overboeking.overschrijving(JAN, spaar, Bedrag.van("250.00"), TransactieType.INLEG, TIJDSTIP, "Inleg");

        assertThat(overboeking.type()).isEqualTo(TransactieType.OVERSCHRIJVING);
        assertThat(overboeking.omschrijving()).isEqualTo("Inleg");
        assertThat(overboeking.boekingen()).containsExactly(
                new Boeking(JAN.iban(), Bedrag.van("-250.00"), spaar, TransactieType.OVERSCHRIJVING),
                new Boeking(spaar.iban(), Bedrag.van("250.00"), JAN, TransactieType.INLEG));
    }

    @Property
    void heeftAltijdTweeKantenOpTweeRekeningenDieSamenNulZijn(
            @ForAll @LongRange(min = 1, max = 99_999_999_999L) long centen,
            @ForAll @LongRange(min = 0, max = 9_999_999_999L) long van,
            @ForAll @LongRange(min = 0, max = 9_999_999_999L) long naar) {
        Assume.that(van != naar);
        var overboeking = new Overboeking(new Partij(Iban.simBank(van), "Van"), new Partij(Iban.simBank(naar), "Naar"),
                new Bedrag(BigDecimal.valueOf(centen, 2)), TransactieType.ONLINE_BANKIEREN, TIJDSTIP);

        List<Boeking> boekingen = overboeking.boekingen();
        assertThat(boekingen).hasSize(2);
        assertThat(boekingen.stream().map(Boeking::rekening).distinct()).hasSize(2);
        assertThat(boekingen.stream().map(boeking -> boeking.bedrag().waarde()).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void weigertEenBedragVanNulOfMinder() {
        assertThatThrownBy(() -> new Overboeking(JAN, SANNE, Bedrag.van("0.00"), TransactieType.ONLINE_BANKIEREN, TIJDSTIP))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Overboeking(JAN, SANNE, Bedrag.van("-0.01"), TransactieType.ONLINE_BANKIEREN, TIJDSTIP))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void weigertDezelfdeRekeningAanBeideKanten() {
        assertThatThrownBy(() -> new Overboeking(JAN, new Partij(JAN.iban(), "Ook Jan"), Bedrag.van("25.00"),
                TransactieType.ONLINE_BANKIEREN, TIJDSTIP))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deUitvoerdatumIsDeDatumVanHetTijdstip() {
        var overboeking = new Overboeking(JAN, SANNE, Bedrag.van("25.00"), TransactieType.ONLINE_BANKIEREN, TIJDSTIP);

        assertThat(overboeking.datum()).isEqualTo(TIJDSTIP.toLocalDate());
    }
}
