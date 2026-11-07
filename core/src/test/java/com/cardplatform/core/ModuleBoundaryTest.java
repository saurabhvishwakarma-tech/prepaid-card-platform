package com.cardplatform.core;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModuleBoundaryTest {

    // Fails if a module reaches into another module's internals or modules depend on each other in a cycle
    @Test
    void modulesOnlyUseEachOthersPublicApi() {
        ApplicationModules.of(CoreApplication.class).verify();
    }
}