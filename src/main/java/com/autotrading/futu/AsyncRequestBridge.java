package com.autotrading.futu;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Bridges the Futu SDK's async callback model to synchronous request/response.
 * <p>
 * Handles the race condition where the SDK callback fires before await() is called:
 * responses are cached in responseCache, and await() checks both the cache and
 * the pending future before blocking.
 */
@Component
public class AsyncRequestBridge {

    private static final Logger log = LoggerFactory.getLogger(AsyncRequestBridge.class);

    /** Pending futures for requests that haven't been awaited yet. */
    private final ConcurrentHashMap<Integer, CompletableFuture<Object>> pending = new ConcurrentHashMap<>();

    /** Cache of responses that arrived before await() was called (race condition). */
    private final ConcurrentHashMap<Integer, Object> responseCache = new ConcurrentHashMap<>();

    private long defaultTimeoutMs = 10000;

    /**
     * Completes a pending request with its response.
     * If no future is registered yet (response arrived early), caches it.
     */
    public void complete(int serialNo, Object response) {
        responseCache.put(serialNo, response);
        CompletableFuture<Object> future = pending.remove(serialNo);
        if (future != null) {
            future.complete(response);
        } else {
            log.debug("Response cached for early serial: {}", serialNo);
        }
    }

    /**
     * Fails all pending requests and clears cache (used during reconnect).
     */
    public void failAll(String reason) {
        int count = 0;
        for (Map.Entry<Integer, CompletableFuture<Object>> entry : pending.entrySet()) {
            entry.getValue().completeExceptionally(new FutuConnectionException(reason));
            count++;
        }
        pending.clear();
        responseCache.clear();
        if (count > 0) {
            log.warn("Failed {} pending requests: {}", count, reason);
        }
    }

    /**
     * Waits synchronously for a request result with default timeout.
     */
    @SuppressWarnings("unchecked")
    public <T> T await(int serialNo, Class<T> responseType) throws FutuRequestException {
        return await(serialNo, responseType, defaultTimeoutMs);
    }

    /**
     * Waits synchronously for a request result with explicit timeout.
     * Checks responseCache first (handles early callback race), then blocks on future.
     */
    @SuppressWarnings("unchecked")
    public <T> T await(int serialNo, Class<T> responseType, long timeoutMs) throws FutuRequestException {
        // Check if response already arrived (race condition: callback fired before await)
        Object cached = responseCache.remove(serialNo);
        if (cached != null) {
            return (T) cached;
        }

        // Register a future to wait for the callback
        CompletableFuture<Object> future = new CompletableFuture<>();
        pending.put(serialNo, future);

        // Double-check cache after registering (callback might have fired between checks)
        cached = responseCache.remove(serialNo);
        if (cached != null) {
            pending.remove(serialNo);
            return (T) cached;
        }

        try {
            Object result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            return (T) result;
        } catch (TimeoutException e) {
            pending.remove(serialNo);
            throw new FutuRequestException("Request timed out after " + timeoutMs + "ms (serial: " + serialNo + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FutuRequestException("Request interrupted (serial: " + serialNo + ")", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new FutuRequestException("Request failed (serial: " + serialNo + "): " + cause.getMessage(), cause);
        }
    }

    public void setDefaultTimeoutMs(long timeoutMs) {
        this.defaultTimeoutMs = timeoutMs;
    }

    public int pendingCount() {
        return pending.size();
    }

    public static class FutuRequestException extends Exception {
        public FutuRequestException(String message) { super(message); }
        public FutuRequestException(String message, Throwable cause) { super(message, cause); }
    }

    public static class FutuConnectionException extends RuntimeException {
        public FutuConnectionException(String message) { super(message); }
    }
}
