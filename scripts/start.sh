#!/usr/bin/env sh
set -eu

if [ "$#" -ne 1 ]; then
  echo "Usage: ./scripts/start.sh <port>" >&2
  exit 1
fi

MARKET_PORT="$1" docker compose up --build --scale app=3
