# Kafka Order Stream Processing System

A comprehensive enterprise-level order processing system built on Apache Kafka, demonstrating advanced messaging patterns including Avro serialization, real-time stream processing, resilient error handling, and dead letter queue management.

## Architecture Overview

This system implements a producer-consumer architecture with the following core components:

- **REST API Server**: Spark Java-based HTTP server for order creation and monitoring
- **Kafka Producer**: Publishes serialized orders to Kafka topics using Avro schemas  
- **Schema Registry**: Manages Avro schemas for data compatibility and evolution
- **Kafka Consumer**: Processes orders with built-in retry logic and aggregation
- **Dead Letter Queue**: Handles permanently failed messages for manual intervention
- **Real-time Aggregator**: Calculates running statistics and price averages

```
┌─────────────┐    ┌──────────────┐    ┌─────────────┐
│  Postman    │────│  REST API    │────│   Kafka     │
│  Client     │    │  Server      │    │  Producer   │
└─────────────┘    │ (Port 35081) │    └─────────────┘
                   └──────────────┘           │
                                              ▼
┌─────────────┐    ┌──────────────┐    ┌─────────────┐
│  Schema     │    │    Kafka     │    │   Orders    │
│ Registry    │    │   Broker     │    │   Topic     │
│ (Port 8081) │    │ (Port 19092) │    └─────────────┘
└─────────────┘    └──────────────┘           │
                                              ▼
┌─────────────┐    ┌──────────────┐    ┌─────────────┐
│   DLQ       │◄───│   Consumer   │◄───│ Processing  │
│  Topic      │    │ (Retry/DLQ)  │    │ & Aggreg.   │
└─────────────┘    └──────────────┘    └─────────────┘
```

## Core Features

- **Event-Driven Architecture**: Asynchronous message processing with Kafka
- **Schema Evolution**: Avro serialization with Confluent Schema Registry
- **Fault Tolerance**: Configurable retry mechanisms for transient failures
- **Error Isolation**: Dead Letter Queue for permanently failed messages
- **Real-Time Processing**: Live order statistics and price aggregation
- **Production Ready**: Comprehensive logging, monitoring, and error handling

## Prerequisites

- **Java 8+**: Required for compilation and runtime
- **Apache Maven 3.6+**: Build and dependency management
- **Docker & Docker Compose**: Infrastructure services

## Quick Start Guide

### 1. Start Infrastructure Services

Launch Kafka, Zookeeper, and Schema Registry:

```bash
docker-compose up -d
```

**Expected Output:**
```
Container zookeeper        Started
Container kafka           Started  
Container schema-registry Started
```

### 2. Build the Project

Compile source code and generate Avro classes:

```bash
mvn clean install
```

**Expected Output:**
```
[INFO] BUILD SUCCESS
[INFO] Total time: 45.234 s
```

### 3. Run the System

#### Option A: Full Demonstration Mode (Recommended)

**Terminal 1** - Start Assignment Consumer:
```bash
mvn exec:java -Dmode=assignment
```

**Expected Output:**
```
KAFKA ASSIGNMENT DEMONSTRATION
=========================================================
ASSIGNMENT REQUIREMENTS IMPLEMENTED:
- Kafka-based system with producer and consumer
- Avro serialization for order messages
- Real-time price aggregation (running average)
- Retry logic for temporary failures
- Dead Letter Queue (DLQ) for permanent failures
- Live system demonstration
- Git repository maintained
=========================================================
OrderConsumer STARTED!
Consumer Group: order-consumer-group-1732185234567
Kafka Servers: localhost:19092
Topic: orders
Poll Timeout: 5000ms
=====================================
Waiting for messages...
```

**Terminal 2** - Start API Server:
```bash
mvn exec:java -Dmode=api
```

**Expected Output:**
```
Working Kafka Order API Server started on port 35081
Kafka brokers: localhost:19092
Schema registry: http://localhost:8081
Available endpoints:
   POST /api/orders - Create new order
   GET /api/orders/stats - Get order statistics
   GET /api/aggregate - Get price aggregations
   GET /api/health - Health check
```

#### Option B: Individual Components

Run consumer and API server separately:

```bash
# Terminal 1: Consumer only
mvn exec:java -Dmode=consumer

# Terminal 2: API server only  
mvn exec:java -Dmode=api
```

## Testing with Postman

### Import Collection

1. Import: `Kafka_Order_Stream_API.postman_collection.json`
2. Set environment variable: `base_url = http://localhost:35081`

### Test Scenarios

The system uses predictable failure patterns based on order ID last digit for reliable demonstration:

**Retry Pattern Rules:**
- **0, 1, 2, 4, 5, 8**: ✅ Process successfully (no failures)
- **3, 6, 9**: 🔄 Simulate temporary failures → Retry 2 times → Process successfully  
- **7**: 🔄 Simulate temporary failures → Retry 2 times → Send to Dead Letter Queue (DLQ)

**Examples:**
- `DEMO-0`, `DEMO-1`, `DEMO-2`, `DEMO-4`, `DEMO-5`, `DEMO-8` → Success immediately
- `DEMO-3`, `DEMO-6`, `DEMO-9` → Fail → Retry → Fail → Retry → Success
- `DEMO-7` → Fail → Retry → Fail → Retry → DLQ

#### Scenario 1: Successful Processing
**Order IDs ending in: 0, 1, 2, 4, 5, 8**

**Request:**
```json
POST http://localhost:35081/api/orders
Content-Type: application/json

{
  "orderId": "DEMO-1",
  "product": "Assignment Coffee", 
  "price": 19.99
}
```

**Console Output:**
```
=====================================
NEW ORDER RECEIVED FROM API!
=====================================
Order ID: DEMO-1
Product: Assignment Coffee
Price: $19.99
Timestamp: 2024-11-21T10:30:45.123Z
Running Statistics:
   Orders Processed: 1
   Running Average: $19.99
   Total Value: $19.99
ORDER SUCCESSFULLY PROCESSED!
=====================================
```

#### Scenario 2: Retry Mechanism  
**Order IDs ending in: 3, 6, 9**

**Request:**
```json
{
  "orderId": "DEMO-3",
  "product": "Retry Demonstration Latte",
  "price": 18.75
}
```

**Console Output:**
```
DEMO: Simulating failure for Order DEMO-3 (Pattern-based)
========================================================
        ASSIGNMENT DEMO: RETRY MECHANISM ACTIVATED!         
========================================================
TEMPORARY FAILURE DETECTED FOR ASSIGNMENT DEMONSTRATION!
Order ID: DEMO-3
Product: Retry Demonstration Latte
Price: $18.75
Current Retry Attempt: 1/2
Failure Reason: Database connection timeout - DEMO
Will retry this order on next poll cycle...
This demonstrates the RETRY LOGIC requirement!
=====================================

========================================================
        ASSIGNMENT DEMO: RETRY MECHANISM ACTIVATED!         
========================================================
TEMPORARY FAILURE DETECTED FOR ASSIGNMENT DEMONSTRATION!
Order ID: DEMO-3
Current Retry Attempt: 2/2
Failure Reason: Network timeout - DEMO
Will retry this order on next poll cycle...
This demonstrates the RETRY LOGIC requirement!
=====================================

Order processed successfully after retries!
Running Statistics:
   Orders Processed: 2
   Running Average: $19.37
   Total Value: $38.74
```

#### Scenario 3: Dead Letter Queue
**Order IDs ending in: 7**

**Request:**
```json
{
  "orderId": "DEMO-7",
  "product": "DLQ Demonstration Order",
  "price": 30.99
}
```

**Console Output:**
```
DEMO: Simulating failure for Order DEMO-7 (Pattern-based)
========================================================
        ASSIGNMENT DEMO: RETRY MECHANISM ACTIVATED!         
========================================================
TEMPORARY FAILURE - Attempt 1/2
========================================================
        ASSIGNMENT DEMO: RETRY MECHANISM ACTIVATED!         
========================================================
TEMPORARY FAILURE - Attempt 2/2

================================================
      ASSIGNMENT DEMO: DEAD LETTER QUEUE ACTIVATED!    
================================================
MAX RETRIES EXCEEDED!
Order ID: DEMO-7
Product: DLQ Demonstration Order
Price: $30.99
Total Retry Attempts: 2
ROUTING TO DEAD LETTER QUEUE (DLQ)
This demonstrates the DLQ requirement!
DLQ OPERATION COMPLETED
=====================================
```

## API Reference

### Health Check
```
GET /api/health
Response: {"status": "API Server is running", "timestamp": "2024-11-21T10:30:45.123Z"}
```

### Create Order
```
POST /api/orders
Content-Type: application/json

Body: {
  "orderId": "string",
  "product": "string", 
  "price": number
}

Response: 201 Created
{
  "message": "Order produced successfully",
  "orderId": "DEMO-1",
  "timestamp": "2024-11-21T10:30:45.123Z"
}
```

### Order Statistics
```
GET /api/orders/stats
Response: {
  "totalOrders": 5,
  "totalValue": 125.48,
  "averagePrice": 25.10,
  "lastUpdated": "2024-11-21T10:30:45.123Z"
}
```

### Price Aggregations
```
GET /api/aggregate
Response: {
  "runningAverage": 25.10,
  "totalOrders": 5,
  "totalValue": 125.48,
  "productStats": {
    "Assignment Coffee": {"count": 2, "avgPrice": 19.99},
    "Premium Laptop": {"count": 1, "avgPrice": 85.50}
  }
}
```

## Configuration

All settings are centralized in `Config.java`:

```java
// Infrastructure
KAFKA_BROKERS = "localhost:19092"
SCHEMA_REGISTRY_URL = "http://localhost:8081"

// Topics  
ORDERS_TOPIC = "orders"
DLQ_TOPIC = "orders-dlq"

// Processing
MAX_RETRY_ATTEMPTS = 2
POLL_TIMEOUT_MS = 5000
API_SERVER_PORT = 35081
```

## Avro Schema

The order schema (`src/main/avro/order.avsc`):

```json
{
  "type": "record",
  "name": "Order",
  "namespace": "com.kafka.assignment",
  "fields": [
    {"name": "orderId", "type": "string"},
    {"name": "product", "type": "string"},
    {"name": "price", "type": "float"}
  ]
}
```

## Available Execution Modes

```bash
# Assignment demonstration with enhanced logging
mvn exec:java -Dmode=assignment

# REST API server only
mvn exec:java -Dmode=api

# Kafka consumer only
mvn exec:java -Dmode=consumer

# Producer test (sends sample orders)
mvn exec:java -Dmode=producer
```

## Project Structure

```
Kafka_Order_Stream_Project_3912/
├── docker-compose.yml                    # Infrastructure services
├── pom.xml                              # Maven configuration
├── Kafka_Order_Stream_API.postman_collection.json  # API tests
├── src/main/
│   ├── avro/
│   │   └── order.avsc                   # Avro schema definition
│   ├── java/com/kafka/assignment/
│   │   ├── SimpleMainApp.java           # Main application entry
│   │   ├── AssignmentConsumerDemo.java  # Assignment demo mode
│   │   ├── WorkingApiServer.java        # REST API implementation
│   │   ├── OrderConsumer.java           # Consumer with retry/DLQ
│   │   ├── OrderProducer.java           # Kafka producer
│   │   ├── Order.java                   # Generated Avro class
│   │   ├── Config.java                  # Configuration constants
│   │   ├── PriceAggregator.java         # Real-time aggregation
│   │   └── OrderValidator.java          # Input validation
│   └── resources/
│       └── log4j2.properties           # Logging configuration
└── target/                             # Compiled classes
```



## Demonstration Checklist

For a complete system demonstration:

1. Start infrastructure: `docker-compose up -d`
2. Build project: `mvn clean install` 
3. Start consumer: `mvn exec:java -Dmode=assignment`
4. Start API: `mvn exec:java -Dmode=api`
5. Test success: Send DEMO-1 order via Postman
6. Test retry: Send DEMO-3 order via Postman  
7. Test DLQ: Send DEMO-7 order via Postman
8. View statistics: GET `/api/orders/stats`
9. Check aggregations: GET `/api/aggregate`

This system successfully demonstrates all enterprise messaging patterns with Kafka, providing a robust foundation for production order processing applications.

