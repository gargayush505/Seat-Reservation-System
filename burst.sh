#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${1:?Usage: ./burst.sh <BASE_URL> [HOT_REQUESTS]}"
HOT_REQUESTS="${2:-2000}"
python3 "$(dirname "$0")/scripts/burst.py" "$BASE_URL" "$HOT_REQUESTS"
