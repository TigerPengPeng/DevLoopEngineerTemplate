package com.autotrading.futu;

import com.autotrading.config.FutuProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the disconnect → reconnect lifecycle at the bridge level.
 *
 * The FutuConnectionManager triggers bridge.failAll() on disconnect,
 * then new requests work normally after reconnect. These tests verify
 * that the bridge correctly handles the full cycle without leaking
 * state between disconnect and reconnect.
 */
class ReconnectLifecycleTest {

    private AsyncRequestBridge bridge;

    @BeforeEach
    void setUp() {
        bridge = new AsyncRequestBridge();
        bridge.setDefaultTimeoutMs(2000);
    }

    @Test
    @DisplayName("Disconnect during active request: future fails, no stale state")
    void testDisconnectFailsPendingRequest() throws Exception {
        int serial = 42;

        // Start a request that will block
        CompletableFuture<String> requestFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return bridge.await(serial, String.class, 5000);
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        });

        // Give it time to register
        Thread.sleep(100);
        assertEquals(1, bridge.pendingCount(), "One pending request before disconnect");

        // Simulate OpenD disconnect: ConnectionManager calls failAll()
        bridge.failAll("Connection lost");

        // The pending request should now fail
        assertThrows(Exception.class, () -> requestFuture.get(2, TimeUnit.SECONDS),
                "Pending request should fail after disconnect");

        assertEquals(0, bridge.pendingCount(), "No pending requests after disconnect");
    }

    @Test
    @DisplayName("Full reconnect cycle: normal → disconnect → reconnect → normal")
    void testFullReconnectCycle() throws Exception {
        // Phase 1: Normal operation — request/response works
        bridge.complete(1, "response-1");
        String r1 = bridge.await(1, String.class, 1000);
        assertEquals("response-1", r1);

        // Phase 2: Disconnect — all state cleared
        bridge.failAll("OpenD connection lost");
        assertEquals(0, bridge.pendingCount());

        // Phase 3: Reconnect — new requests succeed normally
        bridge.complete(2, "response-2");
        String r2 = bridge.await(2, String.class, 1000);
        assertEquals("response-2", r2);

        // Phase 4: Another disconnect and reconnect
        bridge.failAll("Second disconnect");

        bridge.complete(3, "response-3");
        String r3 = bridge.await(3, String.class, 1000);
        assertEquals("response-3", r3);

        // After multiple cycles, no state leaked
        assertEquals(0, bridge.pendingCount());
    }

    @Test
    @DisplayName("Cached response before disconnect is cleared and not returned post-reconnect")
    void testCachedResponseClearedOnDisconnect() {
        // Response arrives early (before await), gets cached
        bridge.complete(500, "cached-response");

        // Disconnect happens before await() ever retrieves it
        bridge.failAll("Connection lost");

        // Post-reconnect: a new request with same serial should NOT
        // return the stale cached value
        assertThrows(AsyncRequestBridge.FutuRequestException.class,
                () -> bridge.await(500, String.class, 100),
                "Stale cache from before disconnect must not survive failAll()");
    }

    @Test
    @DisplayName("Multiple rapid disconnect/reconnect cycles: no state accumulation")
    void testRapidReconnectCycles() throws Exception {
        // Simulate the real-world scenario: OpenD flaps on and off rapidly
        for (int cycle = 0; cycle < 10; cycle++) {
            // Some requests in flight
            int serial = cycle * 10;

            // Register a request
            CompletableFuture<String> f = CompletableFuture.supplyAsync(() -> {
                try {
                    return bridge.await(serial, String.class, 3000);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            Thread.sleep(50);

            // Disconnect
            bridge.failAll("Disconnect cycle " + cycle);
            assertThrows(Exception.class, () -> f.get(1, TimeUnit.SECONDS));

            // Reconnect — clean request works
            bridge.complete(serial + 1, "ok-" + cycle);
            String result = bridge.await(serial + 1, String.class, 1000);
            assertEquals("ok-" + cycle, result);
        }

        // After 10 disconnect/reconnect cycles, bridge should be clean
        assertEquals(0, bridge.pendingCount());
    }

    @Test
    @DisplayName("Simulated polling load: 1000 cycles with periodic failAll, no leak")
    void testPollingLoadWithPeriodicFailAll() throws Exception {
        // This mirrors the real SnapshotPollingService pattern:
        // every 10s it calls getSecuritySnapshot → await() → complete()
        // If OpenD disconnects mid-poll, failAll() fires.
        int totalRequests = 1000;
        int failAllEvery = 100;

        for (int i = 0; i < totalRequests; i++) {
            int serial = i;

            // Complete the request (simulates SDK callback arriving for await)
            // We do complete() slightly before await() to exercise the cache path
            bridge.complete(serial, "data-" + i);
            String result = bridge.await(serial, String.class, 1000);
            assertEquals("data-" + i, result);

            // Periodic disconnect
            if ((i + 1) % failAllEvery == 0) {
                bridge.failAll("Periodic disconnect at " + (i + 1));
            }
        }

        // After 1000 request cycles + 10 disconnects, bridge is clean
        assertEquals(0, bridge.pendingCount());
    }
}
