package com.example.remitlytask.dto;

import java.util.List;

public record SetStocksRequest(List<StockResponse> stocks) {
}
