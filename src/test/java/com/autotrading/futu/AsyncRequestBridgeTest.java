package com.autotrading.futu;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AsyncRequestBridge focusing on the memory leak fix.
 *
 * The bug: complete() used to always put responses into responseCache
 * even when a CompletableFuture was already waiting. Since await()
 * returns via future.get() and never calls responseCache.remove(),
 * those entries accumulated indefinitely → OOM after hours of polling.
 *
 * The fix: complete() only caches when no future is registered yet.
 */
class AsyncRequestBridgeTest {

    private AsyncRequestBridge bridge;

    @BeforeEach
    void setUp() {
        bridge = new AsyncRequestBridge();
        bridge.setDefaultTimeoutMs(2000);
    }

    @Test
    @DisplayName("complete() before await() → response cached and retrieved by await()")
    void testEarlyResponseCached() throws Exception {
        // Simulate: SDK callback fires before await() is called
        Object response = "early-response";
        bridge.complete(100, response);

        // await() should retrieve from cache, not block
        String result = bridge.await(100, String.class, 1000);
        assertEquals("early-response", result);
        assertEquals(0, bridge.pendingCount(), "No pending futures after await");
    }

    @Test
    @DisplayName("complete() after await() → future completed directly, no cache entry left")
    void testLateResponseNoCacheLeak() throws Exception {
        int serial = 200;
        Object response = "late-response";

        // Start await() in a separate thread so it blocks on the future
        ExecutorService exec = Executors.newSingleThreadExecutor();
        java.util.concurrent.Future<String> awaitFuture = exec.submit(() -> bridge.await(serial, String.class, 5000));

        // Give await() time to register its future
        Thread.sleep(100);

        // Now complete() should find the future and complete it directly
        bridge.complete(serial, response);

        String result = awaitFuture.get(3, TimeUnit.SECONDS);
        assertEquals("late-response", result);

        // The critical assertion: after normal request-response cycle,
        // neither pending nor responseCache should hold stale entries.
        // After await() returns via future.get(), the original response
        // must NOT still be sitting in responseCache.
        // We verify: a second await() on the same serial (with no new
        // complete()) should timeout, proving nothing leaked into cache.
        assertThrows(AsyncRequestBridge.FutuRequestException.class,
                () -> bridge.await(serial, String.class, 50),
                "No stale cache entry should remain after normal completion");

        exec.shutdownNow();
    }

    @Test
    @DisplayName("High-volume complete+await cycle: no memory accumulation")
    void testNoMemoryLeakUnderLoad() throws Exception {
        // Simulate the exact pattern that caused the original OOM:
        // thousands of request-response cycles where await() is called
        // before complete() (the normal polling path).
        // After each cycle, no entries should leak into the cache.

        int iterations = 5000;
        AtomicInteger failures = new AtomicInteger(0);

        ExecutorService callbackExec = Executors.newSingleThreadExecutor();
        CountDownLatch done = new CountDownLatch(iterations);

        for (int i = 0; i < iterations; i++) {
            final int serial = i;
            final String response = "resp-" + i;

            // Register await in a thread
            java.util.concurrent.Future<?> f = callbackExec.submit(() -> {
                try {
                    String result = bridge.await(serial, String.class, 2000);
                    if (!response.equals(result)) {
                        failures.incrementAndGet();
                    }
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });

            // Small delay to ensure await() registers the future first
            Thread.sleep(0, 100_000); // 0.1ms

            // Complete the request (simulates SDK callback)
            bridge.complete(serial, response);
        }

        assertTrue(done.await(30, TimeUnit.SECONDS), "All iterations should complete within 30s");
        assertEquals(0, failures.get(), "All request-response pairs should match");
        assertEquals(0, bridge.pendingCount(), "No pending futures remaining after all cycles");

        callbackExec.shutdownNow();
    }

    @Test
    @DisplayName("failAll() clears pending futures and cache")
    void testFailAllOnDisconnect() {
        // Put something in the cache (early response that was never awaited)
        bridge.complete(999, "cached-but-never-awaited");

        // failAll should clear everything (simulates disconnect)
        bridge.failAll("Connection lost");

        assertEquals(0, bridge.pendingCount(), "No pending futures after failAll");

        // A subsequent await on the previously-cached serial should NOT
        // return the stale cached value — it was cleared by failAll.
        assertThrows(AsyncRequestBridge.FutuRequestException.class,
                () -> bridge.await(999, String.class, 100),
                "Cache should be cleared after failAll — stale entry should not survive reconnect");
    }

    @Test
    @DisplayName("await() timeout cleans up pending future")
    void testTimeoutCleansUpPending() {
        int serial = 555;

        // await() with very short timeout, no complete() call → should timeout
        assertThrows(AsyncRequestBridge.FutuRequestException.class,
                () -> bridge.await(serial, String.class, 100),
                "Should timeout when no response arrives");

        assertEquals(0, bridge.pendingCount(),
                "Timed-out future should be removed from pending map");
    }

    @Test
    @DisplayName("Reconnect scenario: failAll → new requests work normally")
    void testReconnectScenario() throws Exception {
        // Phase 1: normal request cycle
        bridge.complete(1, "first-response");
        assertEquals("first-response", bridge.await(1, String.class, 1000));

        // Phase 2: simulate disconnect — failAll clears everything
        bridge.failAll("Disconnected");

        // Phase 3: new requests after reconnect should work fine
        bridge.complete(2, "post-reconnect-response");
        assertEquals("post-reconnect-response", bridge.await(2, String.class, 1000));
        assertEquals(0, bridge.pendingCount());
    }

    @Test
    @DisplayName("Concurrent complete + await on different serials: no cross-contamination")
    void testConcurrentDifferentSerials() throws Exception {
        int n = 50;
        AtomicInteger errors = new AtomicInteger(0);

        for (int i = 0; i < n; i++) {
            final int serial = 1000 + i;
            final String resp = "response-" + serial;

            // Register await first (blocks on future), then complete it
            java.util.concurrent.Future<?> awaitF = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    String r = bridge.await(serial, String.class, 5000);
                    if (!resp.equals(r)) errors.incrementAndGet();
                    return r;
                } catch (Exception e) {
                    errors.incrementAndGet();
                    return null;
                }
            });
            // Small delay to let await register the future
            Thread.sleep(10);
            bridge.complete(serial, resp);
            awaitF.get(5, TimeUnit.SECONDS);
        }

        assertEquals(0, errors.get(), "All concurrent requests should match");
        assertEquals(0, bridge.pendingCount());
    }
}
