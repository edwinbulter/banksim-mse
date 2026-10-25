package nl.banksim.api.transactie;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

import nl.banksim.domain.geld.Bedrag;
import nl.banksim.domain.rekening.Iban;
import nl.banksim.domain.transactie.TransactieType;
import org.junit.jupiter.api.Test;

/** S4.1: Van en Naar volgen uit het teken van het bedrag, gezien vanaf de eigen rekening. */
class TransactieTests {

    static final Partij JAN = new Partij("Jan", Optional.of(Iban.simBank(1L)));
    static final Partij WONINGSTICHTING = new Partij("Woningstichting Thuis", Optional.of(Iban.van("NL91ABNA0417164300")));

    @Test
    void bijEenAfschrijvingIsDeRekeninghouderVanEnDeTegenpartijNaar() {
        Transactie incasso = transactie(Bedrag.van("-812.40"));

        assertThat(incasso.van()).isEqualTo(JAN);
        assertThat(incasso.naar()).isEqualTo(WONINGSTICHTING);
    }

    @Test
    void bijEenBijschrijvingIsDeTegenpartijVanEnDeRekeninghouderNaar() {
        Transactie terugbetaling = transactie(Bedrag.van("12.50"));

        assertThat(terugbetaling.van()).isEqualTo(WONINGSTICHTING);
        assertThat(terugbetaling.naar()).isEqualTo(JAN);
    }

    @Test
    void deTegenpartijIsDeNaamVanDeAndereKant() {
        assertThat(transactie(Bedrag.van("-812.40")).tegenpartij()).isEqualTo("Woningstichting Thuis");
    }

    private static Transactie transactie(Bedrag bedrag) {
        return new Transactie(LocalDate.of(2026, 10, 1), bedrag, JAN, WONINGSTICHTING, TransactieType.INCASSO,
                OffsetDateTime.parse("2026-10-01T06:02:00+02:00"),
                Optional.empty(), Optional.empty());
    }
}
