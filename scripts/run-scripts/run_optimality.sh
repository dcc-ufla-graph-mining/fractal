#!/bin/bash

# Usage: ./run_optimality.sh [metaheuristic] [graph_label_type] [graph_directory] [log_directory]

# Check for required arguments
if [ $# -ne 4 ]; then
    echo "Usage: $0 [metaheuristic] [graph_label_type] [graph_directory] [log_directory]"
    exit 1
fi

# Build the project ONCE before the loops start
echo "Building project..."
./gradlew jar
if [ $? -ne 0 ]; then
    echo "Error: Compilation failed. Exiting."
    exit 1
fi

# Configuration
MEMORY=50
CORES=32
NUM_INIT_VERTICES=(10)
NUM_INIT_SOLUTIONS=(100 1000 10000)
SEED=-1
TIME_LIMIT_MS=(1000 2000 3000)
OBJECTIVE_FUNCTIONS=("conductance" "densesubgraph" "degreeentropy" "triangledensestsubgraph" "labelentropy")

# Map arguments to variables
METAHEURISTIC="$1"
GRAPH_LABEL="$2"
GRAPH_DIR="$3"
PREFIX_LOG_DIR="$4"

REPEATS=5

# Define graph name and log dir
GRAPH_NAME=$(basename "$GRAPH_DIR")
LOG_DIR="$PREFIX_LOG_DIR/optimality/${GRAPH_NAME}"

# Validate graph directory exists
if [ ! -d "$GRAPH_DIR" ]; then
    echo "Error: Graph directory '$GRAPH_DIR' does not exist"
    exit 1
fi

# Create log directory
mkdir -p "$LOG_DIR"

echo "Starting experiments for $GRAPH_NAME..."

# Loop script start
for solutions in "${NUM_INIT_SOLUTIONS[@]}"; do
    for vertices in "${NUM_INIT_VERTICES[@]}"; do
        for timeLimit in "${TIME_LIMIT_MS[@]}"; do
            for objFunc in "${OBJECTIVE_FUNCTIONS[@]}"; do
                for run in $(seq 1 $REPEATS); do

                    # Skip labelentropy for unlabeled graphs
                    if [ "$GRAPH_LABEL" = "unlabeled" ] && [ "$objFunc" = "labelentropy" ]; then
                        # Optional: Log this skip so you know it happened
                        echo "Skipping labelentropy for unlabeled graph (Run $run)"
                        continue
                    fi

                    # Build arguments
                    ARGS="$GRAPH_DIR $vertices $solutions $SEED $timeLimit $objFunc $METAHEURISTIC $GRAPH_LABEL"

                    # Base Log Filename
                    BASENAME="$GRAPH_NAME-$METAHEURISTIC-$CORES-${vertices}-${solutions}-${timeLimit}-${objFunc}-${run}"
                    LOG_FILE="$LOG_DIR/${BASENAME}.txt"

                    # Build the full command
                    EXEC_COMMAND="master_memory=${MEMORY}g app_class=br.ufmg.cs.systems.fractal.apps.SubgraphOptimizationApp worker_cores=${CORES} args=\"$ARGS\" ./bin/fractal-custom-app.sh"

                    echo "Running: $BASENAME"

                    # Execute the command
                    if eval "$EXEC_COMMAND" > "$LOG_FILE" 2>&1; then
                        # --- SUCCESS CASE ---
                        # Compress the successful log
                        gzip -f "$LOG_FILE"
                    else
                        # --- FAILURE CASE ---
                        echo "!!! FAILURE DETECTED: $BASENAME !!!"

                        # Rename the log file to indicate failure
                        mv "$LOG_FILE" "$LOG_DIR/${BASENAME}_FAILED.txt"

                        # gzip -f "$LOG_DIR/${BASENAME}_FAILED.txt"
                    fi

                done
            done
        done
    done
done