package com.example.remitlytask;

record StockItem(String name, long quantity) {
    StockItem {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("Stock name must not be blank");
        }
        if (quantity < 0) {
            throw new BadRequestException("Stock quantity must not be negative");
        }
    }
}
