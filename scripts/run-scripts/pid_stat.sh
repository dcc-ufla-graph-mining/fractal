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
CPU_LOG="${FBASE}cpu_usage_${TARGET_PID}.log"
IO_LOG="${FBASE}io_usage_${TARGET_PID}.log"
MEM_LOG="${FBASE}memory_usage_${TARGET_PID}.log"

# Function to clean up background processes
cleanup() {
    # Kill the pidstat processes
    kill "$PIDSTAT_CPU" "$PIDSTAT_IO" "$PIDSTAT_MEM" 2>/dev/null
}
trap cleanup EXIT

# Run pidstat
pidstat -u -p "$TARGET_PID" 1 > "$CPU_LOG" &
PIDSTAT_CPU=$!

pidstat -d -p "$TARGET_PID" 1 > "$IO_LOG" &
PIDSTAT_IO=$!

pidstat -r -p "$TARGET_PID" 1 > "$MEM_LOG" &
PIDSTAT_MEM=$!

# Wait loop: Check if the monitored process is still alive
while kill -0 "$TARGET_PID" 2>/dev/null; do
    sleep 1
done

# Kill pidstats explicitly
cleanup

# Wait a moment to ensure file handles close
sleep 1

# Compress the logs safely now that writing is done
gzip -f "$CPU_LOG" "$IO_LOG" "$MEM_LOG"