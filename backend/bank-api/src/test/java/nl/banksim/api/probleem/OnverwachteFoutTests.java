package nl.banksim.api.probleem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

/**
 * ADR 0054: een onverwachte fout wordt een probleembericht met status 500, zonder interne details voor de klant; de
 * fout zelf komt in de log, waar het trace-ID bij staat. Fouten van Spring en Spring Security houden hun eigen
 * afhandeling (405, 401, 403).
 */
@ExtendWith(OutputCaptureExtension.class)
class OnverwachteFoutTests {

    private final ProbleemAdvice advice = new ProbleemAdvice();

    @Test
    void eenOnverwachteFoutWordtEen500ZonderInterneDetails() throws Exception {
        ProblemDetail probleem = advice.onverwachteFout(new IllegalStateException("verbinding met de database verbroken"));

        assertThat(probleem.getStatus()).isEqualTo(500);
        assertThat(probleem.getType()).hasToString("urn:banksim:probleem:onverwachte-fout");
        assertThat(probleem.getDetail()).isEqualTo("Er ging iets mis bij de bank. Probeer het later opnieuw.");
    }

    @Test
    void deFoutKomtInDeLog(CapturedOutput uitvoer) throws Exception {
        advice.onverwachteFout(new IllegalStateException("verbinding met de database verbroken"));

        assertThat(uitvoer).contains("Onverwachte fout").contains("verbinding met de database verbroken");
    }

    @Test
    void eenWeigeringVanSpringSecurityBlijftVanSpringSecurity() {
        var fout = new AccessDeniedException("geen beheerder");

        assertThatThrownBy(() -> advice.onverwachteFout(fout)).isSameAs(fout);
    }

    @Test
    void eenFoutVanSpringHoudtZijnEigenStatus() {
        var fout = new HttpRequestMethodNotSupportedException(HttpMethod.DELETE.name());

        assertThatThrownBy(() -> advice.onverwachteFout(fout)).isSameAs(fout);
    }
}
