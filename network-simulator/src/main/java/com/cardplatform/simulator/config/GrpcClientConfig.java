package com.cardplatform.simulator.config;

import com.cardplatform.contracts.network.v1.CardNetworkServiceGrpc;
import io.grpc.ClientInterceptor;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GlobalClientInterceptor;
import org.springframework.grpc.client.ImportGrpcClients;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

// Creates a blocking stub bean for core's CardNetworkService on the "core" channel (see application.yml)
@Configuration
@ImportGrpcClients(target = "core", types = CardNetworkServiceGrpc.CardNetworkServiceBlockingStub.class)
class GrpcClientConfig {

    // Adds the network's client credentials to every call to core
    @Bean
    @GlobalClientInterceptor
    ClientInterceptor networkCredentials(NetworkProperties properties) {
        String credentials = Base64.getEncoder().encodeToString(
                (properties.clientId() + ":" + properties.clientSecret()).getBytes(StandardCharsets.UTF_8));
        Metadata headers = new Metadata();
        headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Basic " + credentials);
        return MetadataUtils.newAttachHeadersInterceptor(headers);
    }
}