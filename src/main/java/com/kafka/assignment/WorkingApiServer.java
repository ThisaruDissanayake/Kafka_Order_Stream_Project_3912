package com.kafka.assignment;

import static spark.Spark.*;
import org.apache.kafka.clients.producer.*;
import io.confluent.kafka.serializers.KafkaAvroSerializer;

import java.util.Properties;
import java.util.concurrent.Future;

public class WorkingApiServer {
    private Producer<String, Order> producer;
    private PriceAggregator aggregator;
    private volatile String lastConsumerOutput = "No consumer output yet";

    public static void main(String[] args) {
        new WorkingApiServer().start();
    }

    public void start() {
        try {
            initProducer();
            aggregator = new PriceAggregator();
        } catch (Exception e) {
            System.err.println("ERROR: Error initializing server: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        // Set port
        port(35081);
        enableCORS("*", "*", "*");

        // Routes without complex JSON parsing
        post("/api/orders", this::handleCreateOrder);
        get("/api/orders/stats", this::handleGetStats);
        get("/api/aggregate", this::handleGetAggregate);
        get("/api/consumer-output", this::handleGetConsumerOutput);
        get("/api/health", this::handleHealthCheck);

        // Start message
        System.out.println("Working Kafka Order API Server started on port 35081");
        System.out.println("Kafka brokers: " + Config.KAFKA_BROKERS);
        System.out.println("Schema registry: " + Config.SCHEMA_REGISTRY_URL);
        System.out.println("Available endpoints:");
        System.out.println("   POST /api/orders - Create new order");
        System.out.println("   GET /api/orders/stats - Get order statistics");
        System.out.println("   GET /api/aggregate - Get price aggregations");
        System.out.println("   GET /api/consumer-output - Get latest consumer output");
        System.out.println("   GET /api/health - Health check");

        awaitInitialization();
        Runtime.getRuntime().addShutdownHook(new Thread(this::cleanup));
    }

    private String handleCreateOrder(spark.Request req, spark.Response res) {
        try {
            // Simple string parsing instead of Jackson
            String requestBody = req.body();
            if (requestBody == null || requestBody.trim().isEmpty()) {
                res.status(400);
                res.type("application/json");
                return "{\"success\":false,\"error\":\"Request body is empty\",\"message\":\"Invalid request\"}";
            }

            // Simple JSON parsing for orderId, product, and price
            String orderId = extractValue(requestBody, "orderId");
            String product = extractValue(requestBody, "product");
            String priceStr = extractValue(requestBody, "price");

            if (orderId == null || product == null || priceStr == null) {
                res.status(400);
                res.type("application/json");
                return "{\"success\":false,\"error\":\"Missing required fields: orderId, product, price\",\"message\":\"Invalid JSON structure\"}";
            }

            float price = Float.parseFloat(priceStr);

            // Create Order object using Avro constructor
            Order order = new Order(orderId, product, Float.valueOf(price));

            // Validate order
            if (!OrderValidator.isValidOrder(order)) {
                res.status(400);
                res.type("application/json");
                return "{\"success\":false,\"error\":\"" + OrderValidator.getValidationError(order) + "\",\"message\":\"Order validation failed\"}";
            }

            // Send to Kafka
            ProducerRecord<String, Order> record = new ProducerRecord<>(Config.ORDERS_TOPIC, orderId, order);
            Future<RecordMetadata> future = producer.send(record);
            RecordMetadata metadata = future.get(); // Wait for completion

            // Update aggregator
            aggregator.processOrder(order);

            res.status(201);
            res.type("application/json");

            System.out.println("Order sent: " + order);

            return "{\"success\":true,\"message\":\"Order produced successfully\",\"orderId\":\"" + orderId +
                   "\",\"product\":\"" + product + "\",\"price\":" + price +
                   ",\"partition\":" + metadata.partition() + ",\"offset\":" + metadata.offset() +
                   ",\"timestamp\":" + metadata.timestamp() + "}";

        } catch (Exception e) {
            res.status(500);
            res.type("application/json");
            return "{\"success\":false,\"error\":\"" + e.getMessage().replace("\"", "'") + "\",\"message\":\"Failed to process order\"}";
        }
    }

    private String extractValue(String json, String key) {
        try {
            String keyPattern = "\"" + key + "\"";
            int startIndex = json.indexOf(keyPattern);
            if (startIndex == -1) return null;

            startIndex = json.indexOf(":", startIndex) + 1;
            while (startIndex < json.length() && Character.isWhitespace(json.charAt(startIndex))) {
                startIndex++;
            }

            if (startIndex >= json.length()) return null;

            int endIndex;
            if (json.charAt(startIndex) == '"') {
                startIndex++; // Skip opening quote
                endIndex = json.indexOf('"', startIndex);
            } else {
                // Number value
                endIndex = startIndex;
                while (endIndex < json.length() &&
                       (Character.isDigit(json.charAt(endIndex)) || json.charAt(endIndex) == '.')) {
                    endIndex++;
                }
            }

            if (endIndex == -1 || endIndex == startIndex) return null;
            return json.substring(startIndex, endIndex);
        } catch (Exception e) {
            return null;
        }
    }

    private String handleGetStats(spark.Request req, spark.Response res) {
        res.type("application/json");

        StringBuilder stats = new StringBuilder();
        stats.append("{\"totalOrders\":").append(aggregator.getTotalOrderCount());
        stats.append(",\"totalValue\":\"$").append(String.format("%.2f", aggregator.getTotalValue())).append("\"");
        stats.append(",\"averagePrice\":\"$").append(String.format("%.2f", aggregator.getRunningAverage())).append("\"");
        stats.append(",\"message\":\"Order statistics retrieved successfully\"}");

        return stats.toString();
    }

    private String handleGetAggregate(spark.Request req, spark.Response res) {
        res.type("application/json");
        return "{\"runningAverage\":\"$" + String.format("%.2f", aggregator.getRunningAverage()) +
               "\",\"totalOrders\":" + aggregator.getTotalOrderCount() +
               ",\"totalValue\":\"$" + String.format("%.2f", aggregator.getTotalValue()) +
               "\",\"message\":\"Price aggregation data\"}";
    }

    private String handleGetConsumerOutput(spark.Request req, spark.Response res) {
        res.type("application/json");
        return "{\"consumerOutput\":\"" + lastConsumerOutput.replace("\"", "'") +
               "\",\"message\":\"Latest consumer output\"}";
    }

    private String handleHealthCheck(spark.Request req, spark.Response res) {
        res.type("application/json");
        return "{\"status\":\"API Server is running\",\"port\":35080,\"kafkaBrokers\":\"" + Config.KAFKA_BROKERS + "\"}";
    }

    public void setConsumerOutput(String output) {
        this.lastConsumerOutput = output;
    }

    private void initProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, Config.KAFKA_BROKERS);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        props.put("schema.registry.url", Config.SCHEMA_REGISTRY_URL);

        // Reliability configs
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 5);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 0);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);

        producer = new KafkaProducer<>(props);
    }

    private static void enableCORS(final String origin, final String methods, final String headers) {
        options("/*", (request, response) -> {
            String accessControlRequestHeaders = request.headers("Access-Control-Request-Headers");
            if (accessControlRequestHeaders != null) {
                response.header("Access-Control-Allow-Headers", accessControlRequestHeaders);
            }

            String accessControlRequestMethod = request.headers("Access-Control-Request-Method");
            if (accessControlRequestMethod != null) {
                response.header("Access-Control-Allow-Methods", accessControlRequestMethod);
            }

            return "OK";
        });

        before((request, response) -> {
            response.header("Access-Control-Allow-Origin", origin);
            response.header("Access-Control-Request-Method", methods);
            response.header("Access-Control-Allow-Headers", headers);
        });
    }

    private void cleanup() {
        System.out.println("Shutting down API server...");
        if (producer != null) {
            producer.close();
        }
        stop();
    }
}
