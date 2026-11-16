package nl.banksim.api.probleem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** ADR 0054: elk probleembericht noemt het trace-ID van het verzoek, zodat de melding naar de trace in Tempo leidt. */
class TraceIdInProbleemTests {

    @Test
    void zetHetTraceIdVanHetVerzoekInHetProbleem() {
        Tracer tracer = mock(Tracer.class, RETURNS_DEEP_STUBS);
        when(tracer.currentTraceContext().context().traceId()).thenReturn("0af7651916cd43dd8448eb211c80319c");
        ProblemDetail probleem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_CONTENT);

        new TraceIdInProbleem(provider(tracer)).beforeBodyWrite(probleem, null, null, null, null, null);

        assertThat(probleem.getProperties()).containsEntry("traceId", "0af7651916cd43dd8448eb211c80319c");
    }

    @Test
    void zonderTraceGeenTraceId() {
        Tracer tracer = mock(Tracer.class, RETURNS_DEEP_STUBS);
        when(tracer.currentTraceContext().context()).thenReturn(null);
        ProblemDetail probleem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);

        new TraceIdInProbleem(provider(tracer)).beforeBodyWrite(probleem, null, null, null, null, null);

        assertThat(probleem.getProperties()).isNull();
    }

    @Test
    void zonderTracerGeenTraceId() {
        ProblemDetail probleem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);

        new TraceIdInProbleem(new StaticListableBeanFactory().getBeanProvider(Tracer.class))
                .beforeBodyWrite(probleem, null, null, null, null, null);

        assertThat(probleem.getProperties()).isNull();
    }

    private static ObjectProvider<Tracer> provider(Tracer tracer) {
        return new StaticListableBeanFactory(Map.of("tracer", tracer)).getBeanProvider(Tracer.class);
    }
}
