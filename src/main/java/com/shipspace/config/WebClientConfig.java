package com.shipspace.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * WebClient configuration with explicit timeouts.
 *
 * WHY TIMEOUTS MATTER HERE:
 * WebClient without timeouts will block a Reactor thread indefinitely if the
 * remote server (GitHub, Multibase) hangs. Under load, this exhausts the
 * Reactor thread pool and takes down the entire service — not just the
 * external call. This is the "slow dependency kills healthy service" failure.
 *
 * Timeout values:
 *   - connect: 5s  — if we can't connect in 5s, the server is unreachable
 *   - read:    10s — GitHub/Multibase should respond within 10s
 *   - write:   10s — our request body should flush within 10s
 *
 * These are enforced at the Netty layer, not the application layer,
 * so they cannot be bypassed by a slow response body.
 */
@Configuration
public class WebClientConfig {

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_SECS  = 10;
    private static final int WRITE_TIMEOUT_SECS = 10;

    @Bean
    public WebClient.Builder webClientBuilder() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MS)
                .responseTimeout(Duration.ofSeconds(READ_TIMEOUT_SECS))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(READ_TIMEOUT_SECS, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(WRITE_TIMEOUT_SECS, TimeUnit.SECONDS)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}
