package com.agentmusic.agentmusic_backend.client.spotify;

import io.netty.resolver.DefaultAddressResolverGroup;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

final class SpotifyWebClientFactory {

    private SpotifyWebClientFactory() {
    }

    static WebClient create(WebClient.Builder webClientBuilder) {
        return create(webClientBuilder, null);
    }

    static WebClient create(WebClient.Builder webClientBuilder, String baseUrl) {
        WebClient.Builder builder = webClientBuilder.clone()
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create().resolver(DefaultAddressResolverGroup.INSTANCE)
                ));

        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }

        return builder.build();
    }
}
