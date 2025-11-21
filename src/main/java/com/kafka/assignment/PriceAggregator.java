package com.kafka.assignment;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class PriceAggregator {
    private final ConcurrentHashMap<String, ProductStats> productStats = new ConcurrentHashMap<>();
    private final AtomicInteger totalOrders = new AtomicInteger(0);
    private final AtomicReference<Double> totalValue = new AtomicReference<>(0.0);

    public static class ProductStats {
        private final AtomicInteger count = new AtomicInteger(0);
        private final AtomicReference<Double> totalPrice = new AtomicReference<>(0.0);
        private final AtomicReference<Double> averagePrice = new AtomicReference<>(0.0);

        public synchronized void addOrder(double price) {
            int newCount = count.incrementAndGet();
            double newTotal = totalPrice.updateAndGet(current -> current + price);
            averagePrice.set(newTotal / newCount);
        }

        public int getCount() { return count.get(); }
        public double getTotalPrice() { return totalPrice.get(); }
        public double getAveragePrice() { return averagePrice.get(); }
    }

    public synchronized void processOrder(Order order) {
        String product = order.getProduct().toString();
        double price = order.getPrice();

        // Update product-specific stats
        productStats.computeIfAbsent(product, k -> new ProductStats()).addOrder(price);

        // Update global stats
        totalOrders.incrementAndGet();
        totalValue.updateAndGet(current -> current + price);
    }

    public synchronized double getRunningAverage() {
        int orders = totalOrders.get();
        return orders > 0 ? totalValue.get() / orders : 0.0;
    }

    public ProductStats getProductStats(String product) {
        return productStats.get(product);
    }

    public ConcurrentHashMap<String, ProductStats> getAllProductStats() {
        return new ConcurrentHashMap<>(productStats);
    }

    public int getTotalOrderCount() {
        return totalOrders.get();
    }

    public double getTotalValue() {
        return totalValue.get();
    }
}
