#!/bin/bash


# Usage: ./perf_stat_monitor.sh [prefix]

PREFIX="$1"
PID=$(pgrep -f "SparkSubmit" | head -n 1)

if [ -z "$PID" ]; then
  echo "SparkSubmit process not found."
  exit 1
fi

echo "Monitoring SparkSubmit (PID=$PID) until exit"
echo

OUTDIR="$HOME/optimization-logs/experiments-2/performance-metrics/temp/livejournal-2"
mkdir -p "$OUTDIR"

FBASE="${OUTDIR}/${PREFIX:+${PREFIX}_}"
OUTFILE="${FBASE}tma_${PID}.txt"

# Run perf stat metrics, output redirected to file
perf stat -p "$PID" >"$OUTFILE" 2>&1

echo "Perf stat output saved to $OUTFILE"

