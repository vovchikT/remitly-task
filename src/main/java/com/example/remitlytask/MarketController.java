package com.example.remitlytask;

import com.example.remitlytask.dto.AuditLogResponse;
import com.example.remitlytask.dto.BankResponse;
import com.example.remitlytask.dto.SetStocksRequest;
import com.example.remitlytask.dto.TradeRequest;
import com.example.remitlytask.dto.WalletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MarketController {
    private final MarketService marketService;

    MarketController(MarketService marketService) {
        this.marketService = marketService;
    }

    @PostMapping("/wallets/{wallet_id}/stocks/{stock_name}")
    ResponseEntity<Void> trade(
            @PathVariable("wallet_id") String walletId,
            @PathVariable("stock_name") String stockName,
            @RequestBody TradeRequest request
    ) {
        marketService.trade(walletId, stockName, request == null ? null : request.type());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/wallets/{wallet_id}")
    WalletResponse getWallet(@PathVariable("wallet_id") String walletId) {
        return marketService.getWallet(walletId);
    }

    @GetMapping("/wallets/{wallet_id}/stocks/{stock_name}")
    long getWalletStockQuantity(
            @PathVariable("wallet_id") String walletId,
            @PathVariable("stock_name") String stockName
    ) {
        return marketService.getWalletStockQuantity(walletId, stockName);
    }

    @GetMapping("/stocks")
    BankResponse getBankStocks() {
        return marketService.getBankStocks();
    }

    @PostMapping("/stocks")
    ResponseEntity<Void> setBankStocks(@RequestBody SetStocksRequest request) {
        marketService.setBankStocks(request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/log")
    AuditLogResponse getAuditLog() {
        return marketService.getAuditLog();
    }

    @PostMapping("/chaos")
    ResponseEntity<Void> chaos() {
        Thread shutdownThread = new Thread(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            System.exit(0);
        }, "chaos-shutdown");
        shutdownThread.setDaemon(false);
        shutdownThread.start();
        return ResponseEntity.ok().build();
    }
}
