package nl.banksim.api.betaling;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** ADR 0054: een geweigerde betaling is een logregel, met het probleemtype en zonder IBAN of naam. */
@ExtendWith(OutputCaptureExtension.class)
class WeigeringInDeLogTests {

    @Test
    void eenWeigeringKomtInDeLog(CapturedOutput uitvoer) {
        new BetalingAdvice().geweigerd(new BetalingGeweigerd(Weigering.SALDO_ONTOEREIKEND, "Saldo ontoereikend"));

        assertThat(uitvoer).contains("Betaling geweigerd: saldo-ontoereikend");
    }
}
