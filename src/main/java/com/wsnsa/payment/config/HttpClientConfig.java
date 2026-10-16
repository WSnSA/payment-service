package com.wsnsa.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
public class HttpClientConfig {

    /**
     * Provider, PosAPI, merchant webhook руу хийх бүх дуудлагад ашиглах RestClient.
     * Base URL нь merchant бүрт өөр тул дуудлага бүр бүтэн URL дамжуулна.
     */
    @Bean
    public RestClient restClient(RestClient.Builder builder, AppProperties props) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(props.http().connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(props.http().readTimeout());
        return builder.requestFactory(factory).build();
    }
}
