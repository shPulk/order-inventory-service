package com.acme.oims.load;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Run with:
 * mvn -q test-compile exec:java -Dexec.classpathScope=test \
 *   -Dexec.mainClass=com.acme.oims.load.LoadTest -Dexec.args="20 30 5"
 * args: virtual users, duration seconds, warm-up seconds.
 * App must already be running on :8080.
 */
public class LoadTest {

    static final String BASE =
            System.getProperty("base", "http://localhost:8080");

    static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public static void main(String[] args) throws Exception {
        int users = args.length > 0 ? Integer.parseInt(args[0]) : 20;
        int seconds = args.length > 1 ? Integer.parseInt(args[1]) : 30;
        int warmup = args.length > 2 ? Integer.parseInt(args[2]) : 5;

        String ops = token("loadtest-ops", "WAREHOUSE_OPS");
        List<Integer> productIds = List.of(1, 2, 3);

        topUp(ops, productIds, 1_000_000);

        System.out.printf(
                "users=%d duration=%ds warmup=%ds%n",
                users,
                seconds,
                warmup
        );

        long start = System.nanoTime();
        long measureFrom =
                start + TimeUnit.SECONDS.toNanos(warmup);
        long end =
                measureFrom + TimeUnit.SECONDS.toNanos(seconds);

        List<Long> latenciesMicros =
                java.util.Collections.synchronizedList(
                        new ArrayList<>()
                );

        Map<Integer, AtomicInteger> statuses =
                new ConcurrentHashMap<>();

        ExecutorService pool =
                Executors.newFixedThreadPool(users);

        double hot = Double.parseDouble(System.getProperty("hot", "0.1"));
        for (int u = 0; u < users; u++) {
            final int id = u;

            pool.submit(() -> {
                String token;

                try {
                    token = token(
                            "load-user-" + id,
                            "CUSTOMER"
                    );
                } catch (Exception e) {
                    return;
                }

                while (System.nanoTime() < end) {

                    int product = ThreadLocalRandom.current().nextDouble() < hot
                            ? 1 : productIds.get(ThreadLocalRandom.current().nextInt(productIds.size()));
                    String body =
                            "{\"lines\":[{\"productId\":" +
                                    product +
                                    ",\"quantity\":1}]}";

                    HttpRequest req =
                            HttpRequest.newBuilder(
                                            URI.create(BASE + "/api/orders")
                                    )
                                    .header(
                                            "Authorization",
                                            "Bearer " + token
                                    )
                                    .header(
                                            "Content-Type",
                                            "application/json"
                                    )
                                    .POST(
                                            HttpRequest.BodyPublishers
                                                    .ofString(body)
                                    )
                                    .build();

                    long t0 = System.nanoTime();

                    int code;

                    try {
                        code = HTTP.send(
                                        req,
                                        HttpResponse.BodyHandlers.discarding()
                                )
                                .statusCode();
                    } catch (Exception e) {
                        code = -1;
                    }

                    long t1 = System.nanoTime();

                    if (t0 >= measureFrom) {
                        latenciesMicros.add(
                                (t1 - t0) / 1000
                        );

                        statuses
                                .computeIfAbsent(
                                        code,
                                        k -> new AtomicInteger()
                                )
                                .incrementAndGet();
                    }
                }
            });
        }

        pool.shutdown();
        pool.awaitTermination(
                seconds + warmup + 60,
                TimeUnit.SECONDS
        );

        Long[] sorted = latenciesMicros.stream()
                .mapToLong(Long::longValue)
                .sorted()
                .boxed()
                .toArray(Long[]::new);

        if (sorted.length == 0) {
            System.out.println(
                    "No measured requests. Is the app running?"
            );
            return;
        }

        System.out.println("---- result ----");

        System.out.printf(
                "requests=%d throughput=%.1f req/s%n",
                sorted.length,
                sorted.length / (double) seconds
        );

        System.out.printf(
                "p50=%.1f ms  p95=%.1f ms  p99=%.1f ms  max=%.1f ms%n",
                pct(sorted, 50),
                pct(sorted, 95),
                pct(sorted, 99),
                sorted[sorted.length - 1] / 1000.0
        );

        System.out.println(
                "status counts: " +
                        new java.util.TreeMap<>(statuses)
        );
    }

    static double pct(Long[] sorted, double p) {
        int idx = (int) Math.ceil(
                (p / 100.0) * sorted.length
        ) - 1;

        return sorted[
                Math.max(
                        0,
                        Math.min(idx, sorted.length - 1)
                )
                ] / 1000.0;
    }

    static String token(
            String subject,
            String role
    ) throws Exception {

        HttpRequest req = HttpRequest.newBuilder(
                        URI.create(BASE + "/api/dev/token")
                )
                .header(
                        "Content-Type",
                        "application/json"
                )
                .POST(
                        HttpRequest.BodyPublishers.ofString(
                                "{\"subject\":\"" +
                                        subject +
                                        "\",\"roles\":[\"" +
                                        role +
                                        "\"]}"
                        )
                )
                .build();

        String body = HTTP.send(
                        req,
                        HttpResponse.BodyHandlers.ofString()
                )
                .body();

        int i = body.indexOf("\"token\":\"") + 9;

        return body.substring(
                i,
                body.indexOf("\"", i)
        );
    }

    /** Sets on_hand high in warehouse 1 so almost every order can reserve. */
    static void topUp(
            String opsToken,
            List<Integer> productIds,
            int onHand
    ) throws Exception {

        for (int p : productIds) {
            HttpRequest req = HttpRequest.newBuilder(
                            URI.create(
                                    BASE + "/api/stock/1/" + p
                            )
                    )
                    .header(
                            "Authorization",
                            "Bearer " + opsToken
                    )
                    .header(
                            "Content-Type",
                            "application/json"
                    )
                    .PUT(
                            HttpRequest.BodyPublishers.ofString(
                                    "{\"onHand\":" + onHand + "}"
                            )
                    )
                    .build();

            int code = HTTP.send(
                            req,
                            HttpResponse.BodyHandlers.discarding()
                    )
                    .statusCode();

            if (code != 200) {
                throw new IllegalStateException(
                        "Stock top-up failed for product " +
                                p +
                                ": " +
                                code
                );
            }
        }
    }
}