package dev.porkchop.trace.configurations;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.webflux.v5_3.SpringWebfluxTelemetry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.WebFilter;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
public class WebClientConfiguration {

    private final SpringWebfluxTelemetry webfluxTelemetry;
    @Value("${swarm.webclient.base-uri}")
    private String baseUri;
    @Value("${swarm.webclient.connect-timeout}")
    private Integer connectTimeout;
    @Value("${swarm.webclient.response-timeout}")
    private Integer responseTimeout;
    @Value("${swarm.webclient.read-timeout}")
    private Integer readTimeout;
    @Value("${swarm.webclient.write-timeout}")
    private Integer writeTimeout;

    public WebClientConfiguration(OpenTelemetry openTelemetry) {
        this.webfluxTelemetry = SpringWebfluxTelemetry.builder(openTelemetry).build();
    }

    // Adds instrumentation to WebClients
    @Bean
    public WebClient.Builder webClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout)
                .responseTimeout(Duration.ofMillis(responseTimeout))
                .doOnConnected(connection ->
                        connection.addHandlerLast(new ReadTimeoutHandler(readTimeout, TimeUnit.MILLISECONDS))
                                .addHandlerLast(new WriteTimeoutHandler(writeTimeout, TimeUnit.MILLISECONDS)));

        return WebClient.create().mutate()
                .baseUrl(baseUri)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .filters(webfluxTelemetry::addClientTracingFilter)
                .filter((request, next) -> {
                    log.debug("[webClientBuilder] headers.size = {}", request.headers().size());
                    request.headers().forEach((name, values) ->
                            log.debug("[webClientBuilder] headers :: {} = {}", name, values));
                    return next.exchange(request);
                })
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }

    // Adds instrumentation to Webflux server
    @Bean
    public WebFilter webFilter() {
        return webfluxTelemetry.createWebFilterAndRegisterReactorHook();
    }
}
