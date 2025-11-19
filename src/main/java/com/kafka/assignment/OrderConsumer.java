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

    private static float totalPrice = 0;
    private static int count = 0;
    private static final Random random = new Random();
    private static final java.util.Map<String, Integer> orderRetryCount = new java.util.HashMap<>();
    private static KafkaProducer<String, String> dlqProducer = null;

    // Stop condition variables
    private static int emptyPollCount = 0;
    private static final int MAX_EMPTY_POLLS = 3; // Stop after 3 consecutive empty polls

    public static void main(String[] args) {

        // Consumer properties
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:19092");

        // Key = String
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer");

        // Value = Avro
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);

        props.put("schema.registry.url", "http://localhost:8081");
        props.put("specific.avro.reader", "true");
        //props.put(ConsumerConfig.GROUP_ID_CONFIG, "order-consumer-demo-" + System.currentTimeMillis());
        //props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "order-consumer-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

        // Performance optimizations
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1024);        // Fetch at least 1KB
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);       // Wait max 500ms
        // props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 30);         // Process up to 30 records per poll
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);    // Manual commit for better control

        String consumerGroup = props.getProperty(ConsumerConfig.GROUP_ID_CONFIG);

        KafkaConsumer<String, Order> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singleton("orders"));

        // Initialize DLQ producer once
        initializeDLQProducer();

        System.out.println("=====================================");
        System.out.println("🚀 OrderConsumer STARTED!");
        System.out.println("📋 Consumer Group: " + consumerGroup);
        System.out.println("📡 Kafka Servers: localhost:19092");
        System.out.println("📨 Topic: orders");
        System.out.println("⏱️  Poll Timeout: 5000ms");
        System.out.println("=====================================");
        System.out.println("⏳ Waiting for messages...");

        boolean keepRunning = true;
        while (true) {
            ConsumerRecords<String, Order> records = consumer.poll(Duration.ofMillis(500));

            for (ConsumerRecord<String, Order> rec : records) {
                try {
                    Order order = rec.value();

                    // Simulated temporary failure → retry for order 5
                    // Check BEFORE processing to simulate temporary failure
                    if (order.getOrderId().toString().equals("5") && retryCount < 1) {
                        retryCount++;
                        throw new TimeoutException("Temporary error!");
                    }

                    // Real-time running average
                    totalPrice += order.getPrice();
                    count++;
                    float avg = totalPrice / count;

                    System.out.println("Consumed: " + order +
                            " | Running Avg Price = " + avg);

                    // Reset retry count after successful processing
                    retryCount = 0;

                } catch (TimeoutException e) {
                    System.out.println("Temporary error → Retrying...");
                    // retry on next poll
                } catch (Exception fatal) {
                    System.out.println("Permanent error → Sending to DLQ...");
                    sendToDLQ(rec);
                }
            }
        }
        // Cleanup and final summary
        try {
            consumer.close();
            if (dlqProducer != null) {
                dlqProducer.close();
            }
            System.out.println("=====================================");
            System.out.println("🎯 CONSUMER COMPLETED SUCCESSFULLY!");
            System.out.println("📊 Processing Summary:");
            System.out.println("   📦 Total Orders Processed: " + count);
            if (count > 0) {
                System.out.println("   💰 Average Order Price: $" + String.format("%.2f", totalPrice / count));
                System.out.println("   💵 Total Order Value: $" + String.format("%.2f", totalPrice));
            }
            System.out.println("=====================================");
        } catch (Exception e) {
            System.err.println("Error during cleanup: " + e.getMessage());
        }
    }

    /**
     * Initialize DLQ producer once for better performance
     */
    private static void initializeDLQProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:19092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        // Performance optimizations
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 5);

        dlqProducer = new KafkaProducer<>(props);
        System.out.println("DLQ Producer initialized");
    }

    /**
     * Sends message to DLQ topic "orders-dlq"
     */
    private static void sendToDLQ(ConsumerRecord<String, Order> rec) {
        try {
            if (dlqProducer != null) {
                dlqProducer.send(new ProducerRecord<>(
                        "orders-dlq",
                        rec.key(),
                        rec.value().toString()
                ));
                System.out.println("✓ Sent to DLQ: " + rec.value().getOrderId());
            } else {
                System.err.println("DLQ Producer not initialized!");
            }
        } catch (Exception ex) {
            System.err.println("Error sending to DLQ: " + ex.getMessage());
        }
    }
}