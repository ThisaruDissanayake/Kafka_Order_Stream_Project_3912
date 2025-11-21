package com.kafka.assignment;

/**
 * Configuration constants for Kafka Order Stream System
 *
 * This class centralizes all configuration values to ensure consistency
 * across the producer, consumer, and API server components.
 */
public class Config {

    // Kafka Infrastructure Configuration
    /** Kafka broker addresses for local development */
    public static final String KAFKA_BROKERS = "localhost:19092";

    /** Confluent Schema Registry URL for Avro schema management */
    public static final String SCHEMA_REGISTRY_URL = "http://localhost:8081";

    // Topic Configuration
    /** Main topic for order messages */
    public static final String ORDERS_TOPIC = "orders";

    /** Dead Letter Queue topic for permanently failed messages */
    public static final String DLQ_TOPIC = "orders-dlq";

    // Consumer Configuration
    /** Base consumer group ID (timestamp will be appended for uniqueness) */
    public static final String CONSUMER_GROUP = "order-consumer-group";

    /** Consumer poll timeout in milliseconds */
    public static final long POLL_TIMEOUT_MS = 5000;

    // Retry Configuration
    /** Maximum number of retry attempts before sending to DLQ */
    public static final int MAX_RETRY_ATTEMPTS = 2;

    // API Server Configuration
    /** Default API server port (overridden in WorkingApiServer) */
    public static final int API_SERVER_PORT = 8080;
}
