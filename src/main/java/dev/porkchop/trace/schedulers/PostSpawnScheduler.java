package dev.porkchop.trace.schedulers;

import dev.porkchop.trace.components.OpentelemetryConnector;
import dev.porkchop.trace.models.Post;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class PostSpawnScheduler {

    private final OpentelemetryConnector opentelemetryConnector;
    private final Tracer tracer;

    @Value("${swarm.importer.scheduler.postSpawn.intervalMillis}")
    private Long intervalMillis;

    @Value("${swarm.importer.scheduler.postSpawn.maxSpwan}")
    private Integer maxSpawn;

    @Value("${swarm.importer.scheduler.postSpawn.shutdownTimeoutSeconds}")
    private Integer shutdownTimeout;

    private AtomicBoolean running = new AtomicBoolean(true);
    private Scheduler scheduler;
    private final WebClient.Builder webclientBuilder;


    public PostSpawnScheduler(OpentelemetryConnector opentelemetryConnector, WebClient.Builder webclientBuilder) {
        this.opentelemetryConnector = opentelemetryConnector;
        this.tracer = this.opentelemetryConnector.getTracer();
        this.webclientBuilder = webclientBuilder;

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.debug("Shutdown signal received");
            running.set(false);
        }));
    }

    @PostConstruct
    private void run() {
        this.scheduler = Schedulers.fromExecutor(Executors.newFixedThreadPool(maxSpawn));

        Flux.interval(Duration.ofMillis(intervalMillis))
                .onBackpressureDrop(tick -> log.debug("[run] Dropping due to backpressure"))
                .takeWhile(tick -> running.get())
                .flatMap(tick -> {
                    log.debug("1.1 **** {}", Span.current());
                    Span span = this.tracer.spanBuilder("run").startSpan();
                    log.debug("1.2 **** {}", span);

                    return getPost()
                            .contextWrite(Context.of(Span.class, span))
                            .subscribeOn(this.scheduler)
                            .doOnNext(post -> {
                                log.debug("[run] post retrieved :: {}", post.getId());
                            })
                            .onErrorResume(error -> {
                                span.recordException(error);
                                span.setStatus(StatusCode.ERROR, "Error in run");
                                log.error("[run] Exception encountered: {}", error.toString());
                                return Mono.empty();
                            })
                            .doFinally(signalType -> {
                                span.end();
                            });
                })
                .subscribe();
    }

    private Mono<Post> getPost() {
        String retrieveUri = "/posts/1";

        return Mono.deferContextual(context -> {
            Span parentSpan = context.getOrDefault(Span.class, null);
            SpanBuilder spanBuilder = this.tracer.spanBuilder("getPost");
            if (parentSpan != null) {
                spanBuilder.setParent(io.opentelemetry.context.Context.current().with(parentSpan));
            }

            Span span = spanBuilder.startSpan();
            span.setAttribute("RetrieveUri", retrieveUri);
            span.addEvent("Calling webservice");
            log.debug("[getPost] Calling webservice :: {}", retrieveUri);

            return this.webclientBuilder.build()
                    .get()
                    .uri(retrieveUri)
                    .retrieve()
                    .bodyToMono(Post.class)
                    .contextWrite(Context.of(Span.class, span))
                    .onErrorResume(WebClientResponseException.class, error -> {
                        if (error.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
                            span.addEvent("No post found");
                            log.debug("[getPost] No post found");
                            return Mono.empty();
                        } else {
                            return Mono.error(error);
                        }
                    })
                    .flatMap(post -> Mono.fromCallable(() -> {
                        return post;
                    }))
                    .doOnNext(post -> {
                        span.setAttribute("id", post.getId());
                        span.setAttribute("title", post.getTitle());
                        span.setAttribute("body", post.getBody());
                        span.setAttribute("userId", post.getUserId());
                        span.addEvent("Returning post");
                        log.debug("[getPost] Returning post :: {}", post.getId());
                    })
                    .doOnError(error -> {
                        span.recordException(error);
                        span.setStatus(StatusCode.ERROR, "Error in getPost");
                        log.error("[getPost] Exception encountered: {}", error.toString());
                    })
                    .doFinally(signalType -> span.end());
        });
    }
}
