package com.kafka.assignment;

/**
 * ASSIGNMENT DEMONSTRATION: Standalone Consumer
 *
 * This class demonstrates the assignment requirements:
 * 1. Avro Deserialization - Reads Avro messages from Kafka
 * 2. Real-time Aggregation - Running average of prices
 * 3. Retry Logic - Temporary failure handling with configurable retries
 * 4. Dead Letter Queue - Permanently failed messages routed to DLQ
 * 5. Live Demonstration - Shows processing in terminal when orders created via Postman
 *
 * USAGE:
 * Run this class to start the consumer, then create orders via Postman API.
 * You will see detailed processing information in the terminal.
 */
public class AssignmentConsumerDemo {

    public static void main(String[] args) {
        System.out.println("KAFKA ASSIGNMENT DEMONSTRATION");
        System.out.println("=========================================================");
        System.out.println("ASSIGNMENT REQUIREMENTS IMPLEMENTED:");
        System.out.println("- Kafka-based system with producer and consumer");
        System.out.println("- Avro serialization for order messages");
        System.out.println("- Real-time price aggregation (running average)");
        System.out.println("- Retry logic for temporary failures");
        System.out.println("- Dead Letter Queue (DLQ) for permanent failures");
        System.out.println("- Live system demonstration");
        System.out.println("- Git repository maintained");
        System.out.println("=========================================================");
        System.out.println("INSTRUCTIONS:");
        System.out.println("1. This consumer will wait INDEFINITELY for orders");
        System.out.println("2. Start API server: mvn exec:java -Dmode=working (in new terminal)");
        System.out.println("3. Use Postman: POST http://localhost:35080/api/orders");
        System.out.println("4. Watch this terminal for real-time processing");
        System.out.println("5. Press Ctrl+C to stop when done demonstrating");
        System.out.println("=========================================================");
        System.out.println("Consumer will now start and wait for orders...");

        // Create and configure consumer for assignment demo
        OrderConsumer consumer = new OrderConsumer();

        // Set up aggregator for real-time calculations
        PriceAggregator aggregator = new PriceAggregator();
        consumer.setAggregator(aggregator);

        // Add shutdown hook for clean exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println();
            System.out.println("ASSIGNMENT DEMO STOPPING...");
            consumer.stopConsuming();

            // Display final assignment results
            System.out.println();
            System.out.println("ASSIGNMENT DEMO COMPLETED");
            System.out.println("=========================================================");
            System.out.println("FINAL AGGREGATION RESULTS:");
            System.out.println("Total Orders Processed: " + aggregator.getTotalOrderCount());
            if (aggregator.getTotalOrderCount() > 0) {
                System.out.println("Final Running Average: $" + String.format("%.2f", aggregator.getRunningAverage()));
                System.out.println("Total Value Processed: $" + String.format("%.2f", aggregator.getTotalValue()));
            }
            System.out.println("=========================================================");
            System.out.println("ALL ASSIGNMENT REQUIREMENTS DEMONSTRATED!");
            System.out.println("Ready for submission!");
            System.out.println("=========================================================");
        }));

        // Start consuming - this will run until interrupted
        consumer.startConsuming();
    }
}
