#!/bin/bash

# Usage: ./run_profiling.sh [metaheuristic] [graph_label_type] [graph_directory] [log_directory]

# Check args
if [ $# -ne 4 ]; then
    echo "Usage: $0 [metaheuristic] [graph_label_type] [graph_directory] [log_directory]"
    exit 1
fi

# Build ONCE
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
NUM_INIT_SOLUTIONS=(100 1000)
SEED=-1
TIME_LIMIT_MS=(1000 2000 3000)
OBJECTIVE_FUNCTIONS=("conductance" "densesubgraph" "degreeentropy" "triangledensestsubgraph" "labelentropy")

# Map arguments
METAHEURISTIC="$1"
GRAPH_LABEL="$2"
GRAPH_DIR="$3"
PREFIX_LOG_DIR="$4"

GRAPH_NAME=$(basename "$GRAPH_DIR")
LOG_DIR="$PREFIX_LOG_DIR/profiling/${GRAPH_NAME}"

# Validate graph directory
if [ ! -d "$GRAPH_DIR" ]; then
    echo "Error: Graph directory '$GRAPH_DIR' does not exist"
    exit 1
fi

mkdir -p "$LOG_DIR"
echo "Starting Profiling experiments for $GRAPH_NAME..."

REPEATS=5

# Loop script start
for solutions in "${NUM_INIT_SOLUTIONS[@]}"; do
    for vertices in "${NUM_INIT_VERTICES[@]}"; do
        for timeLimit in "${TIME_LIMIT_MS[@]}"; do
            for objFunc in "${OBJECTIVE_FUNCTIONS[@]}"; do
                for run in $(seq 1 $REPEATS); do

                    if [ "$GRAPH_LABEL" = "unlabeled" ] && [ "$objFunc" = "labelentropy" ]; then
                        continue
                    fi

                    ARGS="$GRAPH_DIR $vertices $solutions $SEED $timeLimit $objFunc $METAHEURISTIC $GRAPH_LABEL"

                    BASENAME="$GRAPH_NAME-$METAHEURISTIC-${vertices}-${solutions}-${timeLimit}-${objFunc}-${run}"
                    LOG_FILE="$LOG_DIR/${BASENAME}.txt"

                    # Build command
                    EXEC_COMMAND="master_memory=${MEMORY}g app_class=br.ufmg.cs.systems.fractal.apps.SubgraphOptimizationApp worker_cores=${CORES} event=cpu file=\"$LOG_FILE\" args=\"$ARGS\" ./bin/fractal-custom-app-profiling.sh"

                    echo "Profiling: $BASENAME"

                    # Execute (Note: No redirection > because the profiling tool writes the file)
                    if eval "$EXEC_COMMAND"; then
                        # Success
                        gzip -f "$LOG_FILE"
                    else
                        # Failure
                        echo "!!! FAILURE DETECTED: $BASENAME !!!"

                        # Only try to rename if the tool actually created the file
                        if [ -f "$LOG_FILE" ]; then
                            mv "$LOG_FILE" "$LOG_DIR/${BASENAME}_FAILED.txt"
                        else
                            echo "Log file was not created by the profiler."
                        fi
                    fi

                done
            done
        done
    done
done