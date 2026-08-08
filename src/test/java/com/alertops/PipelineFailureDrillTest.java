package com.alertops;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PipelineFailureDrillTest {

    @Test
    void shouldFailOnlyForTheCiDrill() {
        assertTrue(false, "Intentional CI failure drill — do not merge this test");
    }
}