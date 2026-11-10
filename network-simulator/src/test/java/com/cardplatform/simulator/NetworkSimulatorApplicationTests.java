package com.cardplatform.simulator;

import com.cardplatform.simulator.grpc.CoreNetworkClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "simulator.network.client-id=test-network",
        "simulator.network.client-secret=0123456789abcdef0123456789abcdef",
        // The simulator is only a gRPC client; never start a gRPC server in tests
        "spring.grpc.server.enabled=false"})
class NetworkSimulatorApplicationTests {

    @Autowired
    private CoreNetworkClient client;

    @Test
    void startsWithoutCoreRunning() {
        assertThat(client).isNotNull();
    }
}