# Simplified Stock Market

Small Spring Boot service that models a stock market with wallets, one bank, and an audit log.

## Requirements

- Docker with Docker Compose
- Java is not required on the host when using the Docker startup command

## Start

Windows:

```powershell
.\scripts\start.ps1 8080
```

Linux/macOS:

```sh
./scripts/start.sh 8080
```

The service is then available at `http://localhost:8080`.

The Docker setup starts three application instances behind Nginx and stores state in Postgres. Calling `POST /chaos` exits the application instance that handled that request; the remaining instances continue serving traffic and Docker restarts the stopped instance.

## API

Set the bank state:

```sh
curl -X POST http://localhost:8080/stocks \
  -H "Content-Type: application/json" \
  -d '{"stocks":[{"name":"stock1","quantity":99},{"name":"stock2","quantity":1}]}'
```

Get bank state:

```sh
curl http://localhost:8080/stocks
```

Buy or sell one stock:

```sh
curl -X POST http://localhost:8080/wallets/wallet-1/stocks/stock1 \
  -H "Content-Type: application/json" \
  -d '{"type":"buy"}'
```

Get a wallet:

```sh
curl http://localhost:8080/wallets/wallet-1
```

Get one stock quantity in a wallet:

```sh
curl http://localhost:8080/wallets/wallet-1/stocks/stock1
```

Get audit log:

```sh
curl http://localhost:8080/log
```

Kill one serving instance:

```sh
curl -X POST http://localhost:8080/chaos
```

## Behavior

- Initial bank state is empty.
- No wallets exist initially.
- `POST /wallets/{wallet_id}/stocks/{stock_name}` creates the wallet when the stock exists.
- Unknown stocks return `404`.
- Buying a stock with bank quantity `0` returns `400`.
- Selling a stock that the wallet does not own returns `400`.
- Only successful wallet buy/sell operations are written to the audit log.
- `POST /stocks` replaces the current bank stock state.

## Development

Run tests with a local JDK:

```sh
./mvnw test
```

Build and test through Docker:

```sh
docker build -t remitly-task .
```

Stop the Compose stack:

```sh
docker compose down
```

Remove the database volume and return to an empty initial state:

```sh
docker compose down -v
```
