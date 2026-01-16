#!/bin/bash

# Usage: ./tma_metrics.sh [prefix] [output_directory] [PID_TO_MONITOR]

PREFIX="$1"
OUTDIR="$2"
TARGET_PID="$3"

if [ -z "$TARGET_PID" ]; then
  echo "Error: No PID provided to tma_metrics.sh"
  exit 1
fi

mkdir -p "$OUTDIR"

FBASE="${OUTDIR}/${PREFIX:+${PREFIX}_}"
OUTFILE="${FBASE}tma_${TARGET_PID}.txt"

exec perf stat -p "$TARGET_PID" >"$OUTFILE" 2>&1