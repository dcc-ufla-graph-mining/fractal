#!/bin/bash

PREFIX="$1"
OUTDIR="$HOME/optimization-logs/performance_metrics_logs/livejournal-perf-logs"
mkdir -p "$OUTDIR"

FBASE="${OUTDIR}/${PREFIX:+${PREFIX}_}"

# Try multiple times to find the process
for i in {1..5}; do
    PID=$(pgrep -f "SparkSubmit" | head -n 1)
    if [ -n "$PID" ]; then
        break
    fi
    sleep 1
done

if [ -z "$PID" ]; then
    echo "SparkSubmit process not found." > "${FBASE}tma_error.txt"
    exit 1
fi

OUTFILE="${FBASE}tma_${PID}.txt"

# Verify process still exists before attaching perf
if ! ps -p "$PID" > /dev/null; then
    echo "Process $PID already exited" > "$OUTFILE"
    exit 1
fi

# Run perf with timeout
timeout 20 perf stat -p "$PID" > "$OUTFILE" 2>&1

if [ $? -eq 124 ]; then
    echo "perf stat timed out after 20 seconds" >> "$OUTFILE"
fi
