#!/bin/bash

# Navigate to the fractal directory
cd ~/fractal || { echo "Error: fractal directory not found."; exit 1; }

# Define graph to label mapping
declare -A GRAPH_LABELS=(
    ["citeseer"]="vertexlabeled"
    ["amazon"]="unlabeled"
    ["dblp"]="unlabeled"
    ["patents"]="vertexlabeled"
    ["livejournal"]="unlabeled"
    ["youtube"]="vertexlabeled"
)

# Order
GRAPH_ORDER=("citeseer" "amazon"  "dblp" "patents" "livejournal" "youtube")

GRAPHS_DIRECTORY="$HOME/graphs-data"
METAHEURISTIC="ts"

# Base directory for all logs
BASE_LOG_DIR="$HOME/fractal/optimization-logs"

# Directory for individual experiment logs
PREFIX="$BASE_LOG_DIR/ccpe2026/$METAHEURISTIC"

SCRIPTS=("run_optimality.sh" "run_scalability.sh" "run_so_metrics.sh" "run_profiling.sh")

# --- MASTER LOG SETUP ---
# 1. capture timestamp for filename
TIMESTAMP=$(date +"%Y-%m-%d_%H-%M-%S")

# 2. Define specific directory for master logs
MASTER_LOG_DIR="$BASE_LOG_DIR/master-logs"

# 3. Define unique filename (e.g., execution_ils_2026-01-19_09-30.log)
MASTER_LOG="$MASTER_LOG_DIR/execution_${METAHEURISTIC}_${TIMESTAMP}.log"

# 4. Create directory
mkdir -p "$MASTER_LOG_DIR"

# --- TIMER START ---
START_TIME=$(date +%s)
echo "Starting Experiments Batch at $(date)" | tee -a "$MASTER_LOG"
echo "Master Log saved to: $MASTER_LOG" | tee -a "$MASTER_LOG"

# Run each script
for script in "${SCRIPTS[@]}"; do
    SCRIPT_PATH="./scripts/run-scripts/$script"

    if [ ! -x "$SCRIPT_PATH" ]; then
        echo "Error: Script '$SCRIPT_PATH' not executable." | tee -a "$MASTER_LOG"
        continue
    fi

    echo "  >> Running $script..." | tee -a "$MASTER_LOG"


    # Loop through the ordered graphs
    for graph in "${GRAPH_ORDER[@]}"; do
        label="${GRAPH_LABELS[$graph]}"

        # Validation checks
        if [ -z "$label" ]; then
            echo "Warning: Graph '$graph' missing from label map. Skipping." | tee -a "$MASTER_LOG"
            continue
        fi

        if [ ! -d "$GRAPHS_DIRECTORY/$graph" ]; then
            echo "Error: Graph directory '$GRAPHS_DIRECTORY/$graph' not found" | tee -a "$MASTER_LOG"
            continue
        fi

        # Log Graph Start
        echo "==========================================================" | tee -a "$MASTER_LOG"
        echo "Processing graph: $graph (Label: $label) - $(date)" | tee -a "$MASTER_LOG"
        echo "==========================================================" | tee -a "$MASTER_LOG"

        # Execute
        "$SCRIPT_PATH" "$METAHEURISTIC" "$label" "$GRAPHS_DIRECTORY/$graph" "$PREFIX"

        EXIT_CODE=$?
        if [ $EXIT_CODE -ne 0 ]; then
            echo "  !! $script finished with ERRORS for graph $graph (Code: $EXIT_CODE)" | tee -a "$MASTER_LOG"
        else
            echo "  >> $script finished successfully for graph $graph." | tee -a "$MASTER_LOG"
        fi
    done

    echo "$script finished successfully for all graphs" | tee -a "$MASTER_LOG"
    echo "" | tee -a "$MASTER_LOG"
done

# --- TIMER END ---
END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

# Calculate Hours, Minutes, Seconds
HOURS=$((DURATION / 3600))
MINUTES=$(( (DURATION % 3600) / 60 ))
SECONDS=$((DURATION % 60))

# Format the time string (e.g., 02h 15m 30s)
FORMATTED_DURATION=$(printf "%02dh %02dm %02ds" $HOURS $MINUTES $SECONDS)

echo "----------------------------------------------------------" | tee -a "$MASTER_LOG"
echo "All experiments completed at $(date)" | tee -a "$MASTER_LOG"
echo "Total Execution Time: $FORMATTED_DURATION" | tee -a "$MASTER_LOG"
echo "----------------------------------------------------------" | tee -a "$MASTER_LOG"