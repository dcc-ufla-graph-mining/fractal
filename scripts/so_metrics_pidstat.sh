#!/bin/bash

# Usage: ./monitor_spark_pidstat_direct_gz.sh [prefix]
PREFIX="$1"

# Find SparkSubmit Java process PID
PID=$(pgrep -f "java.*SparkSubmit" | head -n 1)

if [ -z "$PID" ]; then
    echo "Error: No SparkSubmit Java process found."
    exit 1
fi

echo "Monitoring SparkSubmit (PID=$PID) until it exits or is killed..."

# Output directory
OUTDIR="$HOME/optimization-logs/performance_metrics_logs"
mkdir -p "$OUTDIR"

# Build output file names with optional prefix
FBASE="${OUTDIR}/${PREFIX:+${PREFIX}_}"  # only add underscore if prefix is not empty
CPU_LOG="${FBASE}cpu_usage_${PID}.log.gz"
IO_LOG="${FBASE}io_usage_${PID}.log.gz"
MEM_LOG="${FBASE}memory_usage_${PID}.log.gz"

# Run pidstat and pipe directly into gzip-compressed log files
pidstat -u -p "$PID" 1 | gzip > "$CPU_LOG" &
PIDSTAT_CPU=$!

pidstat -d -p "$PID" 1 | gzip > "$IO_LOG" &
PIDSTAT_IO=$!

pidstat -r -p "$PID" 1 | gzip > "$MEM_LOG" &
PIDSTAT_MEM=$!

# Wait until the process exits or is killed
while kill -0 "$PID" 2>/dev/null; do
    sleep 1
done

# Kill pidstat collectors
kill "$PIDSTAT_CPU" "$PIDSTAT_IO" "$PIDSTAT_MEM" 2>/dev/null
sleep 1

echo "Logs saved to $OUTDIR:"
ls -lh "$OUTDIR"/*.gz
