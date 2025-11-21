package com.kafka.assignment;

/**
 * Main application entry point for Kafka Order Stream System
 * Supports three main modes: assignment demo, API server, consumer, and producer
 */
public class SimpleMainApp {
    public static void main(String[] args) {
        System.out.println("Starting Kafka Order Stream System...");

        String mode = System.getProperty("mode", "test");

        switch (mode.toLowerCase()) {
            case "test":
                System.out.println("System initialized successfully!");
                printUsage();
                break;
            case "assignment":
            case "demo":
                startAssignmentDemo();
                break;
            case "working":
            case "api":
                startWorkingApiServer();
                break;
            case "consumer":
                startConsumer();
                break;
            case "producer":
                runProducer();
                break;
            default:
                System.out.println("ERROR: Invalid mode: " + mode);
                printUsage();
        }
    }

    private static void startWorkingApiServer() {
        System.out.println("Starting API Server mode...");
        try {
            WorkingApiServer apiServer = new WorkingApiServer();
            apiServer.start();
        } catch (Exception e) {
            System.err.println("ERROR: Error starting API server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void startConsumer() {
        System.out.println("Starting Consumer mode...");
        try {
            OrderConsumer consumer = new OrderConsumer();
            consumer.startConsuming();
        } catch (Exception e) {
            System.err.println("ERROR: Error starting consumer: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void runProducer() {
        System.out.println("Running Producer mode...");
        try {
            OrderProducer.main(new String[]{});
        } catch (Exception e) {
            System.err.println("ERROR: Producer error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void startAssignmentDemo() {
        System.out.println("Starting Assignment Demonstration mode...");
        try {
            AssignmentConsumerDemo.main(new String[]{});
        } catch (Exception e) {
            System.err.println("ERROR: Assignment demo error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void printUsage() {
        System.out.println("Available modes:");
        System.out.println("  -Dmode=assignment - Assignment demo (consumer + detailed output)");
        System.out.println("  -Dmode=api       - Start REST API server");
        System.out.println("  -Dmode=consumer  - Start Kafka consumer only");
        System.out.println("  -Dmode=producer  - Run producer to send sample orders");
        System.out.println();
        System.out.println("For assignment demonstration: mvn exec:java -Dmode=assignment");
    }
}
