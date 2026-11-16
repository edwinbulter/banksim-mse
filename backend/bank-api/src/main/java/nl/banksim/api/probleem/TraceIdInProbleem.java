package nl.banksim.api.probleem;

import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * ADR 0054: elk probleembericht krijgt het trace-ID van het verzoek (veld {@code traceId} in het contract). Zo leidt
 * een melding naar de trace in Tempo en de logregels in Loki.
 */
@RestControllerAdvice
class TraceIdInProbleem implements ResponseBodyAdvice<ProblemDetail> {

    private final Tracer tracer;

    /** Zonder tracing (de slicetests van de controllers) een tracer die niets doet: dan komt er geen traceId bij. */
    TraceIdInProbleem(ObjectProvider<Tracer> tracer) {
        this.tracer = tracer.getIfAvailable(() -> Tracer.NOOP);
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return ProblemDetail.class.isAssignableFrom(returnType.getParameterType());
    }

    @Override
    public ProblemDetail beforeBodyWrite(ProblemDetail probleem, MethodParameter returnType, MediaType contentType,
            Class<? extends HttpMessageConverter<?>> converterType, ServerHttpRequest request, ServerHttpResponse response) {
        TraceContext context = tracer.currentTraceContext().context();
        if (probleem != null && context != null && !context.traceId().isEmpty()) {
            probleem.setProperty("traceId", context.traceId());
        }
        return probleem;
    }
}
