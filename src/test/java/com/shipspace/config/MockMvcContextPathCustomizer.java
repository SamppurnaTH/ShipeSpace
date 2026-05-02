package com.shipspace.config;

import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.setup.ConfigurableMockMvcBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Propagates server.servlet.context-path=/api to all MockMvc requests in @WebMvcTest slices.
 *
 * Spring Boot's embedded-server context-path is NOT automatically stripped in MockMvc
 * (@WebMvcTest does not start a real servlet container). Without this, requests to
 * "/api/social/follow/user456" fail to match the controller's "/social/follow/{userId}"
 * because the DispatcherServlet sees the full URI instead of the path relative to the
 * context root.
 *
 * We register a defaultRequest with contextPath="/api" so that MockMvcRequestBuilder.merge()
 * propagates contextPath (and the derived servletPath) to every test request that does not
 * set it explicitly -- which is all of them.
 *
 * Import via @Import(MockMvcContextPathCustomizer.class) in each @WebMvcTest class.
 */
@TestConfiguration
public class MockMvcContextPathCustomizer {

    @Bean
    public MockMvcBuilderCustomizer contextPathCustomizer() {
        return new MockMvcBuilderCustomizer() {
            @Override
            public void customize(ConfigurableMockMvcBuilder<?> builder) {
                builder.defaultRequest(get("/").contextPath("/api"));
            }
        };
    }
}
