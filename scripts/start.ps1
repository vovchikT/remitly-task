param(
    [Parameter(Mandatory = $true)]
    [int] $Port
)

$ErrorActionPreference = "Stop"
$env:MARKET_PORT = "$Port"
docker compose up --build --scale app=3
