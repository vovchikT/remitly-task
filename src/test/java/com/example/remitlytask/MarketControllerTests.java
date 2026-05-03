package com.example.remitlytask;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MarketControllerTests {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.update("DELETE FROM audit_log");
        jdbcTemplate.update("DELETE FROM wallet_stock");
        jdbcTemplate.update("DELETE FROM wallet");
        jdbcTemplate.update("DELETE FROM bank_stock");
    }

    @Test
    void buysAndSellsSingleStocksAndLogsSuccessfulWalletOperations() throws Exception {
        HttpResponse<String> setStocksResponse = postJson(
                "/stocks",
                "{\"stocks\":[{\"name\":\"remitly\",\"quantity\":2}]}"
        );

        assertThat(setStocksResponse.statusCode()).isEqualTo(HttpStatus.OK.value());

        HttpResponse<String> buyResponse = postJson(
                "/wallets/wallet-1/stocks/remitly",
                "{\"type\":\"buy\"}"
        );
        assertThat(buyResponse.statusCode()).isEqualTo(HttpStatus.OK.value());

        HttpResponse<String> walletStockResponse = get("/wallets/wallet-1/stocks/remitly");
        assertThat(walletStockResponse.body()).isEqualTo("1");

        HttpResponse<String> bankResponse = get("/stocks");
        assertThat(bankResponse.body()).isEqualTo("{\"stocks\":[{\"name\":\"remitly\",\"quantity\":1}]}");

        HttpResponse<String> sellResponse = postJson(
                "/wallets/wallet-1/stocks/remitly",
                "{\"type\":\"sell\"}"
        );
        assertThat(sellResponse.statusCode()).isEqualTo(HttpStatus.OK.value());

        HttpResponse<String> logResponse = get("/log");
        assertThat(logResponse.body()).isEqualTo(
                "{\"log\":[{\"type\":\"buy\",\"wallet_id\":\"wallet-1\",\"stock_name\":\"remitly\"},"
                        + "{\"type\":\"sell\",\"wallet_id\":\"wallet-1\",\"stock_name\":\"remitly\"}]}"
        );
    }

    @Test
    void returnsExpectedErrorsForMissingOrUnavailableStocks() throws Exception {
        postJson(
                "/stocks",
                "{\"stocks\":[{\"name\":\"empty\",\"quantity\":0}]}"
        );

        HttpResponse<String> unknownStockResponse = postJson(
                "/wallets/wallet-1/stocks/unknown",
                "{\"type\":\"buy\"}"
        );
        assertThat(unknownStockResponse.statusCode()).isEqualTo(HttpStatus.NOT_FOUND.value());

        HttpResponse<String> unavailableBankStockResponse = postJson(
                "/wallets/wallet-1/stocks/empty",
                "{\"type\":\"buy\"}"
        );
        assertThat(unavailableBankStockResponse.statusCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());

        HttpResponse<String> unavailableWalletStockResponse = postJson(
                "/wallets/wallet-1/stocks/empty",
                "{\"type\":\"sell\"}"
        );
        assertThat(unavailableWalletStockResponse.statusCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());

        HttpResponse<String> logResponse = get("/log");
        assertThat(logResponse.body()).isEqualTo("{\"log\":[]}");
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri(path)).GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postJson(String path, String json) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
