package nl.banksim.api.telemetrie;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * ADR 0054: de appender OTEL uit logback-spring.xml krijgt de OpenTelemetry van Spring Boot; pas dan gaan de
 * logregels, met hun trace-ID, naar Loki. Zonder OpenTelemetry (een slicetest) blijft de appender stil.
 */
@Component
class LogsNaarOpenTelemetry implements InitializingBean {

    private final ObjectProvider<OpenTelemetry> openTelemetry;

    LogsNaarOpenTelemetry(ObjectProvider<OpenTelemetry> openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    @Override
    public void afterPropertiesSet() {
        openTelemetry.ifAvailable(OpenTelemetryAppender::install);
    }
}
