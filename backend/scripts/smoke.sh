#!/usr/bin/env bash
# End-to-end check against a running server: two users, one real message,
# polled until the pipeline finishes.
#
#   npm run dev            # in another terminal
#   ./scripts/smoke.sh samples/marathi1.m4a 13.7
set -euo pipefail

BASE="${BASE:-http://localhost:8787}"
AUDIO="${1:-samples/marathi1.m4a}"
DURATION="${2:-}"
FROM_USER="${FROM_USER:-sunita}"
TO_USER="${TO_USER:-lakshmi}"

[ -f "$AUDIO" ] || { echo "No such file: $AUDIO"; exit 1; }

jqf() { python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('$1',''))"; }

echo "health:  $(curl -fsS "$BASE/health")"

curl -fsS -X POST "$BASE/users" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$FROM_USER\",\"language\":\"mr-IN\"}" > /dev/null
curl -fsS -X POST "$BASE/users" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$TO_USER\",\"language\":\"kn-IN\"}" > /dev/null
echo "users:   $FROM_USER (mr-IN) -> $TO_USER (kn-IN)"

ARGS=(-F "sender=$FROM_USER" -F "recipient=$TO_USER" -F "audio=@$AUDIO")
[ -n "$DURATION" ] && ARGS+=(-F "durationSeconds=$DURATION")

RESPONSE=$(curl -fsS -X POST "$BASE/messages" "${ARGS[@]}")
ID=$(echo "$RESPONSE" | jqf id)
[ -n "$ID" ] || { echo "Send failed: $RESPONSE"; exit 1; }
echo "sent:    $ID"

PREVIOUS=""
for _ in $(seq 1 90); do
  MESSAGE=$(curl -fsS "$BASE/messages/$ID")
  STATUS=$(echo "$MESSAGE" | jqf status)
  [ "$STATUS" != "$PREVIOUS" ] && { echo "         $STATUS"; PREVIOUS="$STATUS"; }
  case "$STATUS" in
    ready)  echo "$MESSAGE" | python3 -m json.tool; echo "OK"; exit 0 ;;
    failed) echo "$MESSAGE" | python3 -m json.tool; echo "FAILED"; exit 1 ;;
  esac
  sleep 2
done

echo "Timed out waiting for $ID"; exit 1
