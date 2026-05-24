package dev.gkit.pipeline;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PipelineTest {

    @Test
    @DisplayName("process transforms all items and preserves order")
    void processTransformsAllItemsInOrder() throws Exception {
        List<Integer> inputs = List.of(1, 2, 3, 4, 5);
        List<Integer> results = Pipeline.process(inputs, n -> n * 2, 3);
        assertEquals(List.of(2, 4, 6, 8, 10), results);
    }

    @Test
    @DisplayName("process with empty list returns empty list")
    void processWithEmptyListReturnsEmpty() throws Exception {
        List<String> results = Pipeline.process(List.<String>of(), (String s) -> s.toUpperCase(), 2);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("process propagates exception from stage")
    void processThrowsOnStageException() {
        List<Integer> inputs = List.of(1, 2, 3);
        assertThrows(Exception.class, () ->
                Pipeline.process(inputs, n -> {
                    if (n == 2) throw new RuntimeException("error on 2");
                    return n;
                }, 2)
        );
    }

    @Test
    @DisplayName("process uses given number of workers")
    void processUsesConfiguredWorkers() throws Exception {
        List<Integer> inputs = List.of(1, 2, 3, 4);
        // Just verify it works with workers=1 (sequential) and workers=4 (parallel)
        List<Integer> seq = Pipeline.process(inputs, n -> n + 10, 1);
        List<Integer> par = Pipeline.process(inputs, n -> n + 10, 4);
        assertEquals(seq, par);
    }

    @Test
    @DisplayName("process with workers=0 uses items.size() workers")
    void processWithZeroWorkersFallsBackToItemsSize() throws Exception {
        List<Integer> inputs = List.of(10, 20, 30);
        List<Integer> results = Pipeline.process(inputs, n -> n / 10, 0);
        assertEquals(List.of(1, 2, 3), results);
    }

    @Test
    @DisplayName("chain applies stages sequentially")
    void chainAppliesStagesSequentially() throws Exception {
        Pipeline.Stage<Integer, Integer> chain = Pipeline.chain(
                n -> n + 1,
                n -> n * 2,
                n -> n - 3
        );
        int result = chain.apply(5); // (5+1)*2 - 3 = 9
        assertEquals(9, result);
    }

    @Test
    @DisplayName("chain with single stage acts as identity transform")
    void chainWithSingleStage() throws Exception {
        Pipeline.Stage<String, String> chain = Pipeline.chain(s -> s.toUpperCase());
        assertEquals("HELLO", chain.apply("hello"));
    }

    @Test
    @DisplayName("compose creates A->B->C pipeline")
    void composeCreatesTwoStageChain() throws Exception {
        Pipeline.Stage<String, Integer> parse = Integer::parseInt;
        Pipeline.Stage<Integer, String> format = n -> "val=" + (n * 2);
        Pipeline.Stage<String, String> composed = Pipeline.compose(parse, format);
        assertEquals("val=84", composed.apply("42"));
    }

    @Test
    @DisplayName("Pipeline.of builder chains stages fluently")
    void pipelineBuilderChainsStages() throws Exception {
        String result = Pipeline.of("42")
                .then(Integer::parseInt)
                .then(n -> n * 2)
                .then(n -> "result=" + n)
                .get();
        assertEquals("result=84", result);
    }

    @Test
    @DisplayName("Pipeline.of builder get() returns current value")
    void pipelineBuilderGetReturnsCurrent() throws Exception {
        int result = Pipeline.of(10)
                .then(n -> n + 5)
                .get();
        assertEquals(15, result);
    }

    @Test
    @DisplayName("process runs concurrently with multiple workers")
    void processRunsConcurrently() throws Exception {
        AtomicInteger maxConcurrent = new AtomicInteger(0);
        AtomicInteger current = new AtomicInteger(0);

        List<Integer> inputs = List.of(1, 2, 3, 4, 5, 6);
        Pipeline.process(inputs, n -> {
            int c = current.incrementAndGet();
            maxConcurrent.updateAndGet(m -> Math.max(m, c));
            Thread.sleep(20);
            current.decrementAndGet();
            return n;
        }, 4);

        // With 4 workers and 6 items, concurrency should exceed 1
        assertTrue(maxConcurrent.get() > 1, "Expected concurrent execution with 4 workers");
    }

    @Test
    @DisplayName("compose propagates exception from first stage")
    void composePropagatesExceptionFromFirstStage() {
        Pipeline.Stage<String, Integer> failingParse = s -> { throw new RuntimeException("bad input"); };
        Pipeline.Stage<Integer, String> second = n -> "v=" + n;
        Pipeline.Stage<String, String> composed = Pipeline.compose(failingParse, second);
        assertThrows(RuntimeException.class, () -> composed.apply("bad"));
    }
}
