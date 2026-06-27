package com.payshack.payin.utils;

import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Embedded HTTP server that receives POST callbacks from the payment provider.
 *
 * The provider POSTs to:  https://<ngrok-url>/payin/callback
 * ngrok forwards that to: http://localhost:<port>/payin/callback
 * This server captures the body and queues it for the test to consume.
 */
public class CallbackReceiver {

    private static final Logger log = LoggerFactory.getLogger(CallbackReceiver.class);

    /** Path the provider must POST to. Appended to the ngrok public URL. */
    public static final String CALLBACK_PATH = "/payin/callback";

    private final HttpServer server;
    private final BlockingQueue<String> queue = new ArrayBlockingQueue<>(100);

    public CallbackReceiver(int port) throws Exception {
        // Bind to IPv6 wildcard (::) — accepts both IPv4 and IPv6 connections.
        // Needed on Windows 11 where localhost resolves to ::1 (IPv6).
        InetSocketAddress bindAddr;
        try {
            bindAddr = new InetSocketAddress(InetAddress.getByName("::"), port);
        } catch (Exception e) {
            bindAddr = new InetSocketAddress(InetAddress.getByName("0.0.0.0"), port);
        }

        server = HttpServer.create(bindAddr, 0);
        server.setExecutor(Executors.newSingleThreadExecutor());

        server.createContext(CALLBACK_PATH, exchange -> {
            try {
                if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    String body = new String(
                            exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    log.info("Provider callback received:\n{}", body);
                    queue.offer(body);

                    byte[] ok = "OK".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, ok.length);
                    exchange.getResponseBody().write(ok);
                } else {
                    exchange.sendResponseHeaders(405, -1);
                }
            } catch (Exception e) {
                log.error("Error handling callback", e);
            } finally {
                exchange.close();
            }
        });

        server.start();
        log.info("Callback receiver listening on port {} at {}", port, CALLBACK_PATH);
    }

    /** Drops any callbacks queued from a previous payment before starting a new one. */
    public void clearQueue() {
        queue.clear();
    }

    /**
     * Blocks until the provider sends a callback or the timeout elapses.
     * @return raw JSON body, or {@code null} on timeout
     */
    public String waitForCallback(long timeoutSeconds) throws InterruptedException {
        log.info("Waiting up to {}s for provider callback...", timeoutSeconds);
        return queue.poll(timeoutSeconds, TimeUnit.SECONDS);
    }

    /** Shuts down the embedded HTTP server. */
    public void stop() {
        server.stop(0);
        log.info("Callback receiver stopped.");
    }
}
