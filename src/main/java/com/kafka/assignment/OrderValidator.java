package com.kafka.assignment;

public class OrderValidator {

    public static boolean isValidOrder(Order order) {
        if (order == null) {
            return false;
        }

        if (order.getOrderId() == null || order.getOrderId().toString().trim().isEmpty()) {
            return false;
        }

        if (order.getProduct() == null || order.getProduct().toString().trim().isEmpty()) {
            return false;
        }

        if (order.getPrice() <= 0) {
            return false;
        }

        return true;
    }

    public static String getValidationError(Order order) {
        if (order == null) {
            return "Order cannot be null";
        }

        if (order.getOrderId() == null || order.getOrderId().toString().trim().isEmpty()) {
            return "Order ID cannot be null or empty";
        }

        if (order.getProduct() == null || order.getProduct().toString().trim().isEmpty()) {
            return "Product cannot be null or empty";
        }

        if (order.getPrice() <= 0) {
            return "Price must be greater than 0";
        }

        return null;
    }
}
