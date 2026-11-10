package com.cardplatform.simulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class NetworkSimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(NetworkSimulatorApplication.class, args);
    }
}