package dev.porkchop.trace.components;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Data
@Component
public class OpentelemetryConnector {

    private static final String TRACE_ID = "trace_id";
    private static final String SPAN_ID = "span_id";

    @Value("${app.groupId}")
    private String appGroupID;

    @Value("${app.artifactId}")
    private String appArtifactId;

    @Value("${app.version}")
    private String appVersion;

    private OpenTelemetry openTelemetry;
    private Tracer tracer;
    private Meter meter;

    @PostConstruct
    private void init() {
        this.openTelemetry = GlobalOpenTelemetry.get();
        this.tracer = this.openTelemetry
                .getTracer(this.appGroupID + "." + this.appArtifactId, this.appVersion);
        this.meter = this.openTelemetry
                .getMeter(this.appGroupID + "." + this.appArtifactId);
    }

    public static Boolean isOtelAgentActive() {
        return System.getProperty("otel.javaagent.version") != null;
    }
}
