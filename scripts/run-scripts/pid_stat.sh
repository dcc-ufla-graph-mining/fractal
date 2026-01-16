#!/bin/bash

# Usage: ./pid_stat.sh [prefix] [output_directory] [PID_TO_MONITOR]

PREFIX="$1"
OUTDIR="$2"
TARGET_PID="$3"

if [ -z "$TARGET_PID" ]; then
    echo "Error: No PID provided to pid_stat.sh"
    exit 1
fi

mkdir -p "$OUTDIR"

# Build filenames
FBASE="${OUTDIR}/${PREFIX:+${PREFIX}_}"
CPU_LOG="${FBASE}cpu_usage_${TARGET_PID}.log.gz"
IO_LOG="${FBASE}io_usage_${TARGET_PID}.log.gz"
MEM_LOG="${FBASE}memory_usage_${TARGET_PID}.log.gz"

# Ensure background pidstat processes AND gzip pipes are killed on exit
cleanup() {
    # Kill the pidstat processes
    kill "$PIDSTAT_CPU" "$PIDSTAT_IO" "$PIDSTAT_MEM" 2>/dev/null
    # Kill any lingering child processes of this shell (like gzip)
    pkill -P $$ 2>/dev/null
}
trap cleanup EXIT

# Run pidstat
pidstat -u -p "$TARGET_PID" 1 | gzip > "$CPU_LOG" &
PIDSTAT_CPU=$!

pidstat -d -p "$TARGET_PID" 1 | gzip > "$IO_LOG" &
PIDSTAT_IO=$!

pidstat -r -p "$TARGET_PID" 1 | gzip > "$MEM_LOG" &
PIDSTAT_MEM=$!

# Wait loop: Check if the monitored process is still alive
while kill -0 "$TARGET_PID" 2>/dev/null; do
    sleep 1
done