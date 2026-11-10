package com.cardplatform.core.payments.config;

import io.grpc.Status;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.grpc.server.exception.GrpcExceptionHandler;
import org.springframework.grpc.server.security.AuthenticationProcessInterceptor;
import org.springframework.grpc.server.security.GrpcSecurity;
import org.springframework.grpc.server.security.HttpBasicAuthenticationExtractor;
import org.springframework.security.authentication.ProviderManager;

@Configuration
class NetworkGrpcConfig {

    // Replaces Spring Boot's default gRPC security, which would demand a user JWT on every call.
    // The card network is a system, not a user, so it authenticates with its own client credentials.
    // httpBasic() is not used: it also registers a username/password provider that needs a
    // UserDetailsService, while the network is checked only by NetworkClientAuthenticationProvider.
    @Bean
    @GlobalServerInterceptor
    AuthenticationProcessInterceptor networkAuthenticationInterceptor(GrpcSecurity grpc,
                                                                      NetworkClientProperties network) throws Exception {
        return grpc
                .authenticationManager(new ProviderManager(new NetworkClientAuthenticationProvider(network)))
                .authenticationExtractor(new HttpBasicAuthenticationExtractor())
                .authorizeRequests(requests -> requests
                        .methods("cardplatform.network.v1.CardNetworkService/*").hasAuthority("ROLE_NETWORK")
                        // gRPC health checks and reflection
                        .methods("grpc.*/*").permitAll()
                        .allRequests().denyAll())
                .build();
    }

    // A malformed request is the caller's mistake: INVALID_ARGUMENT, not an internal error
    @Bean
    GrpcExceptionHandler invalidNetworkRequestHandler() {
        return exception -> exception instanceof IllegalArgumentException
                ? Status.INVALID_ARGUMENT.withDescription(exception.getMessage()).asException()
                : null;
    }
}