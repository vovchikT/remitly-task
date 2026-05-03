package com.example.remitlytask;

import com.example.remitlytask.dto.BankResponse;
import com.example.remitlytask.dto.LogEntryResponse;
import com.example.remitlytask.dto.AuditLogResponse;
import com.example.remitlytask.dto.SetStocksRequest;
import com.example.remitlytask.dto.StockResponse;
import com.example.remitlytask.dto.WalletResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;

@Service
class MarketService {
    private final JdbcTemplate jdbcTemplate;

    MarketService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    void setBankStocks(SetStocksRequest request) {
        if (request == null || request.stocks() == null) {
            throw new BadRequestException("Stocks list is required");
        }

        jdbcTemplate.update("DELETE FROM bank_stock");
        Set<String> stockNames = new HashSet<>();
        for (StockResponse stock : request.stocks()) {
            StockItem item = validateStock(stock);
            if (!stockNames.add(item.name())) {
                throw new BadRequestException("Stock names must be unique");
            }
            jdbcTemplate.update(
                    "INSERT INTO bank_stock(name, quantity) VALUES (?, ?)",
                    item.name(),
                    item.quantity()
            );
        }
    }

    BankResponse getBankStocks() {
        List<StockResponse> stocks = jdbcTemplate.query(
                "SELECT name, quantity FROM bank_stock ORDER BY name",
                (rs, rowNum) -> new StockResponse(rs.getString("name"), rs.getLong("quantity"))
        );
        return new BankResponse(stocks);
    }

    WalletResponse getWallet(String walletId) {
        ensureWalletExists(walletId);
        return new WalletResponse(walletId, getWalletStocks(walletId));
    }

    long getWalletStockQuantity(String walletId, String stockName) {
        ensureWalletExists(walletId);
        ensureStockExists(stockName);
        Long quantity = jdbcTemplate.queryForObject(
                "SELECT COALESCE((SELECT quantity FROM wallet_stock WHERE wallet_id = ? AND stock_name = ?), 0)",
                Long.class,
                walletId,
                stockName
        );
        return quantity == null ? 0 : quantity;
    }

    @Transactional
    void trade(String walletId, String stockName, String rawType) {
        TradeType type = parseTradeType(rawType);
        long bankQuantity = lockBankStock(stockName);
        createWalletIfMissing(walletId);

        if (type == TradeType.buy) {
            buy(walletId, stockName, bankQuantity);
        } else {
            sell(walletId, stockName, bankQuantity);
        }

        jdbcTemplate.update(
                "INSERT INTO audit_log(type, wallet_id, stock_name) VALUES (?, ?, ?)",
                type.name(),
                walletId,
                stockName
        );
    }

    AuditLogResponse getAuditLog() {
        List<LogEntryResponse> entries = jdbcTemplate.query(
                "SELECT type, wallet_id, stock_name FROM audit_log ORDER BY id",
                (rs, rowNum) -> new LogEntryResponse(
                        rs.getString("type"),
                        rs.getString("wallet_id"),
                        rs.getString("stock_name")
                )
        );
        return new AuditLogResponse(entries);
    }

    private List<StockResponse> getWalletStocks(String walletId) {
        return jdbcTemplate.query(
                "SELECT stock_name, quantity FROM wallet_stock WHERE wallet_id = ? AND quantity > 0 ORDER BY stock_name",
                (rs, rowNum) -> new StockResponse(rs.getString("stock_name"), rs.getLong("quantity")),
                walletId
        );
    }

    private void buy(String walletId, String stockName, long bankQuantity) {
        if (bankQuantity <= 0) {
            throw new BadRequestException("No stock available in the bank");
        }

        jdbcTemplate.update("UPDATE bank_stock SET quantity = quantity - 1 WHERE name = ?", stockName);
        Long walletQuantity = lockWalletStock(walletId, stockName);
        if (walletQuantity == null) {
            jdbcTemplate.update(
                    "INSERT INTO wallet_stock(wallet_id, stock_name, quantity) VALUES (?, ?, 1)",
                    walletId,
                    stockName
            );
        } else {
            jdbcTemplate.update(
                    "UPDATE wallet_stock SET quantity = quantity + 1 WHERE wallet_id = ? AND stock_name = ?",
                    walletId,
                    stockName
            );
        }
    }

    private void sell(String walletId, String stockName, long bankQuantity) {
        Long walletQuantity = lockWalletStock(walletId, stockName);
        if (walletQuantity == null || walletQuantity <= 0) {
            throw new BadRequestException("No stock available in the wallet");
        }

        jdbcTemplate.update("UPDATE bank_stock SET quantity = ? WHERE name = ?", bankQuantity + 1, stockName);
        jdbcTemplate.update(
                "UPDATE wallet_stock SET quantity = quantity - 1 WHERE wallet_id = ? AND stock_name = ?",
                walletId,
                stockName
        );
    }

    private long lockBankStock(String stockName) {
        try {
            Long quantity = jdbcTemplate.queryForObject(
                    "SELECT quantity FROM bank_stock WHERE name = ? FOR UPDATE",
                    Long.class,
                    stockName
            );
            return quantity == null ? 0 : quantity;
        } catch (EmptyResultDataAccessException exception) {
            throw new NotFoundException("Stock does not exist");
        }
    }

    private Long lockWalletStock(String walletId, String stockName) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT quantity FROM wallet_stock WHERE wallet_id = ? AND stock_name = ? FOR UPDATE",
                    Long.class,
                    walletId,
                    stockName
            );
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private void ensureWalletExists(String walletId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wallet WHERE id = ?",
                Integer.class,
                walletId
        );
        if (count == null || count == 0) {
            throw new NotFoundException("Wallet does not exist");
        }
    }

    private void createWalletIfMissing(String walletId) {
        try {
            jdbcTemplate.update("INSERT INTO wallet(id) VALUES (?)", walletId);
        } catch (DuplicateKeyException ignored) {
            // The wallet may already exist or may have been created by another instance.
        }
    }

    private void ensureStockExists(String stockName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bank_stock WHERE name = ?",
                Integer.class,
                stockName
        );
        if (count == null || count == 0) {
            throw new NotFoundException("Stock does not exist");
        }
    }

    private TradeType parseTradeType(String rawType) {
        if (rawType == null) {
            throw new BadRequestException("Trade type is required");
        }
        try {
            return TradeType.valueOf(rawType.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Trade type must be buy or sell");
        }
    }

    private StockItem validateStock(StockResponse stock) {
        if (stock == null) {
            throw new BadRequestException("Stock entry must not be null");
        }
        return new StockItem(stock.name(), stock.quantity());
    }
}
