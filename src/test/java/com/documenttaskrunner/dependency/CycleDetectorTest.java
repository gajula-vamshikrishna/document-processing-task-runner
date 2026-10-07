package com.documenttaskrunner.dependency;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CycleDetectorTest {
    private final CycleDetector detector = new CycleDetector();

    @Test
    void validGraphHasNoCycle() {
        Map<String, List<String>> graph = Map.of(
                "validate-document", List.of(),
                "extract-data", List.of("validate-document"),
                "classify-document", List.of("validate-document"),
                "validate-data", List.of("extract-data", "classify-document"),
                "generate-json", List.of("validate-data"),
                "store-result", List.of("generate-json")
        );

        assertTrue(detector.detectCycle(graph).isEmpty());
    }

    @Test
    void directCycleIsDetected() {
        Map<String, List<String>> graph = Map.of(
                "A", List.of("B"),
                "B", List.of("A")
        );

        assertFalse(detector.detectCycle(graph).isEmpty());
    }

    @Test
    void indirectCycleIsDetected() {
        Map<String, List<String>> graph = Map.of(
                "A", List.of("B"),
                "B", List.of("C"),
                "C", List.of("A")
        );

        assertFalse(detector.detectCycle(graph).isEmpty());
    }
}
