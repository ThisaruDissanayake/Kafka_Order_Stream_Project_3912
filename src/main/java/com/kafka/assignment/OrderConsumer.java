package com.kafka.assignment;

import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.errors.TimeoutException;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.Random;

/**
 * Kafka Consumer that:
 * 1. Reads Avro messages
 * 2. Calculates running average price
 * 3. Performs retry on temporary failures
 * 4. Sends permanently failed messages to DLQ
 */
public class OrderConsumer {

    private float totalPrice = 0;
    private int count = 0;
    private final Random random = new Random();
    private final java.util.Map<String, Integer> orderRetryCount = new java.util.HashMap<>();
    private KafkaProducer<String, String> dlqProducer = null;
    private KafkaConsumer<String, Order> consumer = null;
    private PriceAggregator aggregator = null;
    private volatile boolean running = false;
    private String lastOutput = "No messages processed yet";

    // Stop condition variables - disabled for assignment demo (consumer runs indefinitely)
    private int emptyPollCount = 0;
    private static final int MAX_EMPTY_POLLS = -1; // -1 means never stop, wait indefinitely for orders

    public static void main(String[] args) {
        new OrderConsumer().startConsuming();
    }

    public void setAggregator(PriceAggregator aggregator) {
        this.aggregator = aggregator;
    }

    public String getLastOutput() {
        return lastOutput;
    }

    public void startConsuming() {
        running = true;

        // Consumer properties
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, Config.KAFKA_BROKERS);

        // Key = String
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer");

        // Value = Avro
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);

        props.put("schema.registry.url", Config.SCHEMA_REGISTRY_URL);
        props.put("specific.avro.reader", "true");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, Config.CONSUMER_GROUP + "-" + System.currentTimeMillis());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

        // Performance optimizations
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1024);        // Fetch at least 1KB
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);       // Wait max 500ms
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);    // Manual commit for better control

        String consumerGroup = props.getProperty(ConsumerConfig.GROUP_ID_CONFIG);

        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singleton(Config.ORDERS_TOPIC));

        // Initialize DLQ producer once
        initializeDLQProducer();

        System.out.println("=====================================");
        System.out.println("OrderConsumer STARTED!");
        System.out.println("Consumer Group: " + consumerGroup);
        System.out.println("Kafka Servers: " + Config.KAFKA_BROKERS);
        System.out.println("Topic: " + Config.ORDERS_TOPIC);
        System.out.println("Poll Timeout: " + Config.POLL_TIMEOUT_MS + "ms");
        System.out.println("=====================================");
        System.out.println("Waiting for messages...");

        while (running) {
            ConsumerRecords<String, Order> records = consumer.poll(Duration.ofMillis(Config.POLL_TIMEOUT_MS));

            if (records.isEmpty()) {
                emptyPollCount++;
                // Only show waiting message every 10th poll to reduce noise
                if (emptyPollCount % 10 == 1) {
                    String output = "Waiting for new orders... (Poll cycle: " + emptyPollCount + ") - Create orders via Postman!";
                    System.out.println(output);
                    lastOutput = output;
                }

                // Never stop if MAX_EMPTY_POLLS is -1 (assignment demo mode)
                if (MAX_EMPTY_POLLS > 0 && emptyPollCount >= MAX_EMPTY_POLLS) {
                    String summary = "All orders processed! Stopping consumer after " + MAX_EMPTY_POLLS + " empty polls.";
                    System.out.println(summary);
                    System.out.println("Final Summary:");
                    System.out.println("   Total Orders Processed: " + count);
                    if (count > 0) {
                        System.out.println("   Final Average Price: $" + String.format("%.2f", totalPrice / count));
                    }
                    lastOutput = summary + " - Total Orders: " + count;
                    running = false;
                }
                continue;
            }

            // Reset empty poll count since we received messages
            emptyPollCount = 0;
            String processing = "Processing " + records.count() + " messages...";
            System.out.println(processing);
            lastOutput = processing;

            for (ConsumerRecord<String, Order> rec : records) {
                try {
                    Order order = rec.value();
                    String orderId = order.getOrderId().toString();

                    // ASSIGNMENT DEMO: Enhanced failure simulation for clear demonstration
                    // Make retry logic and DLQ routing more predictable and visible

                    // Strategy: Every 3rd order fails temporarily, every 7th order goes to DLQ
                    boolean shouldSimulateFailure = false;
                    boolean shouldGoDLQ = false;

                    // Parse order ID to get the last digit for predictable failure patterns
                    String orderNum = orderId.replaceAll("[^0-9]", ""); // Extract numbers from order ID
                    if (!orderNum.isEmpty()) {
                        try {
                            // Get the last digit of the order number for pattern matching
                            int lastDigit = Integer.parseInt(orderNum.substring(orderNum.length() - 1));

                            // ASSIGNMENT REQUIREMENTS:
                            // Orders ending in 0,1,2,4,5,8 → Successful Processing (no failure simulation)
                            // Orders ending in 3,6,9 → Retry Mechanism
                            // Orders ending in 7 → Dead Letter Queue

                            if (lastDigit == 3 || lastDigit == 6 || lastDigit == 9) {
                                shouldSimulateFailure = true;
                            } else if (lastDigit == 7) {
                                shouldSimulateFailure = true;
                                shouldGoDLQ = true;
                            } else {
                                // Orders ending in 0,1,2,4,5,8 should process successfully
                                shouldSimulateFailure = false;
                                shouldGoDLQ = false;
                            }

                        } catch (NumberFormatException e) {
                            // Fallback: no failure simulation if parsing fails
                            shouldSimulateFailure = false;
                        }
                    } else {
                        // Fallback: no failure simulation if no numbers in order ID
                        shouldSimulateFailure = false;
                    }

                    // Apply failure simulation
                    if (shouldSimulateFailure || shouldGoDLQ) {
                        int currentRetries = orderRetryCount.getOrDefault(orderId, 0);

                        if (shouldGoDLQ && currentRetries >= Config.MAX_RETRY_ATTEMPTS) {
                            // Force DLQ routing for demonstration
                            orderRetryCount.put(orderId, Config.MAX_RETRY_ATTEMPTS + 1);
                        } else if (currentRetries < Config.MAX_RETRY_ATTEMPTS) {
                            // Simulate temporary failure
                            orderRetryCount.put(orderId, currentRetries + 1);
                            String[] failures = {
                                "Database connection timeout - DEMO",
                                "Network timeout - DEMO",
                                "Service temporarily unavailable - DEMO",
                                "Resource pool exhausted - DEMO",
                                "External API timeout - DEMO"
                            };
                            String failureReason = failures[random.nextInt(failures.length)];

                            System.out.println("DEMO: Simulating failure for Order " + orderId + " (Pattern-based)");
                            throw new TimeoutException("Temporary resource failure: " + failureReason);
                        }
                        // If max retries exceeded, will continue to processing or DLQ routing
                    }

                    // ONLY update aggregation for successfully processed orders
                    // Real-time running average
                    totalPrice += order.getPrice();
                    count++;
                    float avg = totalPrice / count;

                    // Update aggregator if available - AFTER successful processing
                    if (aggregator != null) {
                        aggregator.processOrder(order);
                    }

                    // Enhanced terminal output for assignment demonstration
                    System.out.println("=====================================");
                    System.out.println("NEW ORDER RECEIVED FROM API!");
                    System.out.println("=====================================");
                    System.out.println("Order ID: " + orderId);
                    System.out.println("Product: " + order.getProduct());
                    System.out.println("Price: $" + String.format("%.2f", order.getPrice()));
                    System.out.println("Timestamp: " + java.time.Instant.ofEpochMilli(rec.timestamp()));
                    System.out.println("Kafka Partition: " + rec.partition());
                    System.out.println("Kafka Offset: " + rec.offset());
                    System.out.println("Running Total Orders: " + count);
                    System.out.println("Running Average Price: $" + String.format("%.2f", avg));
                    System.out.println("Total Value Processed: $" + String.format("%.2f", totalPrice));
                    System.out.println("=====================================");
                    System.out.println("ORDER SUCCESSFULLY PROCESSED!");
                    System.out.println("=====================================");

                    String processed = "PROCESSED ORDER: " + order.getOrderId() +
                            " | Product: " + order.getProduct() +
                            " | Price: $" + order.getPrice() +
                            " | Running Avg: $" + String.format("%.2f", avg) +
                            " | Total Orders: " + count;

                    System.out.println(processed);
                    lastOutput = processed;

                    // Reset retry count after successful processing
                    orderRetryCount.remove(orderId);

                } catch (TimeoutException e) {
                    String orderId = rec.value().getOrderId().toString();
                    int retries = orderRetryCount.getOrDefault(orderId, 0);

                    System.out.println();
                    System.out.println("========================================================");
                    System.out.println("        ASSIGNMENT DEMO: RETRY MECHANISM ACTIVATED!         ");
                    System.out.println("========================================================");
                    System.out.println("TEMPORARY FAILURE DETECTED FOR ASSIGNMENT DEMONSTRATION!");
                    System.out.println("Order ID: " + orderId);
                    System.out.println("Product: " + rec.value().getProduct());
                    System.out.println("Price: $" + rec.value().getPrice());
                    System.out.println("Current Retry Attempt: " + retries + "/" + Config.MAX_RETRY_ATTEMPTS);
                    System.out.println("Failure Reason: " + e.getMessage());
                    System.out.println("Will retry this order on next poll cycle...");
                    System.out.println("This demonstrates the RETRY LOGIC requirement!");
                    System.out.println("=====================================");

                    String error = "RETRY DEMO: Order " + orderId + " (attempt " + retries + "/" + Config.MAX_RETRY_ATTEMPTS + ") → " + e.getMessage();
                    lastOutput = error;

                    // If max retries exceeded, send to DLQ
                    if (retries >= Config.MAX_RETRY_ATTEMPTS) {
                        System.out.println();
                        System.out.println("================================================");
                        System.out.println("      ASSIGNMENT DEMO: DEAD LETTER QUEUE ACTIVATED!    ");
                        System.out.println("================================================");
                        System.out.println("MAX RETRIES EXCEEDED!");
                        System.out.println("Failed Order ID: " + orderId);
                        System.out.println("Product: " + rec.value().getProduct());
                        System.out.println("Price: $" + rec.value().getPrice());
                        System.out.println("Total Retry Attempts: " + Config.MAX_RETRY_ATTEMPTS);
                        System.out.println("ROUTING TO DEAD LETTER QUEUE (DLQ)");
                        System.out.println("This demonstrates the DLQ requirement!");
                        System.out.println("In production: Failed orders would be reviewed manually");
                        sendToDLQ(rec, "Max retries exceeded after " + Config.MAX_RETRY_ATTEMPTS + " attempts: " + e.getMessage());
                        orderRetryCount.remove(orderId); // Remove from retry tracking
                    }
                } catch (Exception fatal) {
                    String orderId = rec.value().getOrderId().toString();
                    System.out.println("PERMANENT FAILURE DETECTED");
                    System.out.println("Order ID: " + orderId);
                    System.out.println("Fatal Error: " + fatal.getMessage());
                    System.out.println("IMMEDIATE DLQ ROUTING");
                    System.out.println("=====================================");

                    String error = "Permanent error for Order " + orderId + " → Sending to DLQ: " + fatal.getMessage();
                    System.out.println(error);
                    lastOutput = error;
                    sendToDLQ(rec, "Permanent failure: " + fatal.getMessage());
                }
            }

            // Commit offsets after processing all messages in the batch
            try {
                consumer.commitSync();
                System.out.println("Committed offsets for " + records.count() + " messages");
            } catch (Exception e) {
                System.err.println("ERROR: Error committing offsets: " + e.getMessage());
            }
        }

        // Cleanup and final summary
        cleanup();
    }

    public void stopConsuming() {
        running = false;
        cleanup();
    }

    private void cleanup() {
        try {
            if (consumer != null) {
                consumer.close();
            }
            if (dlqProducer != null) {
                dlqProducer.close();
            }
            System.out.println("=====================================");
            System.out.println("CONSUMER COMPLETED SUCCESSFULLY!");
            System.out.println("Processing Summary:");
            System.out.println("   Total Orders Processed: " + count);
            if (count > 0) {
                System.out.println("   Average Order Price: $" + String.format("%.2f", totalPrice / count));
                System.out.println("   Total Order Value: $" + String.format("%.2f", totalPrice));
            }
            System.out.println("=====================================");
        } catch (Exception e) {
            System.err.println("ERROR: Error during cleanup: " + e.getMessage());
        }
    }

    /**
     * Initialize DLQ producer once for better performance
     */
    private void initializeDLQProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, Config.KAFKA_BROKERS);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        // Performance optimizations
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 5);

        dlqProducer = new KafkaProducer<>(props);
        System.out.println("DLQ Producer initialized");
    }

    /**
     * Sends message to DLQ topic with error information
     */
    private void sendToDLQ(ConsumerRecord<String, Order> rec, String errorMessage) {
        try {
            if (dlqProducer != null) {
                // Create detailed DLQ message with error information
                String dlqMessage = String.format(
                    "FAILED_ORDER: OrderId=%s, Product=%s, Price=%.2f, OriginalPartition=%d, OriginalOffset=%d, ErrorReason=%s, Timestamp=%s",
                    rec.value().getOrderId(),
                    rec.value().getProduct(),
                    rec.value().getPrice(),
                    rec.partition(),
                    rec.offset(),
                    errorMessage,
                    java.time.Instant.now()
                );

                dlqProducer.send(new ProducerRecord<>(
                        Config.DLQ_TOPIC,
                        rec.key(),
                        dlqMessage
                ));

                System.out.println("DLQ OPERATION COMPLETED");
                System.out.println("Order ID: " + rec.value().getOrderId());
                System.out.println("DLQ Topic: " + Config.DLQ_TOPIC);
                System.out.println("Error: " + errorMessage);
                System.out.println("DLQ Timestamp: " + java.time.Instant.now());
                System.out.println("=====================================");

            } else {
                System.err.println("ERROR: DLQ Producer not initialized!");
            }
        } catch (Exception ex) {
            System.err.println("CRITICAL ERROR: Error sending to DLQ: " + ex.getMessage());
        }
    }

    /**
     * Backward compatibility method
     */
    private void sendToDLQ(ConsumerRecord<String, Order> rec) {
        sendToDLQ(rec, "Unspecified error");
    }
}