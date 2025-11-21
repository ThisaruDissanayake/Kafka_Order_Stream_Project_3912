package com.kafka.assignment;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

public class PriceAggregator {
    private final ConcurrentHashMap<String, ProductStats> productStats = new ConcurrentHashMap<>();
    private final AtomicLong totalOrders = new AtomicLong(0);
    private final DoubleAdder totalValue = new DoubleAdder();

    public static class ProductStats {
        private final AtomicLong count = new AtomicLong(0);
        private final DoubleAdder totalPrice = new DoubleAdder();

        public synchronized void addOrder(double price) {
            totalPrice.add(price);
            count.incrementAndGet();
        }

        public long getCount() { return count.get(); }
        public double getTotalPrice() { return totalPrice.sum(); }
        public double getAveragePrice() {
            long orders = count.get();
            return orders == 0 ? 0.0 : totalPrice.sum() / orders;
        }
    }

    public void processOrder(Order order) {
        String product = order.getProduct().toString();
        double price = order.getPrice();

        // Update product-specific stats
        productStats.computeIfAbsent(product, k -> new ProductStats()).addOrder(price);

        // Update global stats - add value first, then increment count to avoid division by zero
        totalValue.add(price);
        totalOrders.incrementAndGet();
    }

    public double getRunningAverage() {
        long orders = totalOrders.get();
        return orders == 0 ? 0.0 : totalValue.sum() / orders;
    }

    public ProductStats getProductStats(String product) {
        return productStats.get(product);
    }

    public ConcurrentHashMap<String, ProductStats> getAllProductStats() {
        return new ConcurrentHashMap<>(productStats);
    }

    public long getTotalOrderCount() {
        return totalOrders.get();
    }

    public double getTotalValue() {
        return totalValue.sum();
    }
}
