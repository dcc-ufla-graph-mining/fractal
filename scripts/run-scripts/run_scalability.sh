#!/bin/bash

# Usage: ./run_scalability.sh [metaheuristic] [graph_label_type] [graph_directory] [log_directory]

# Check for required arguments
if [ $# -ne 4 ]; then
    echo "Usage: $0 [metaheuristic] [graph_label_type] [graph_directory] [log_directory]"
    exit 1
fi

# Build the project ONCE
echo "Building project..."
./gradlew jar
if [ $? -ne 0 ]; then
    echo "Error: Compilation failed. Exiting."
    exit 1
fi

# Configuration
MEMORY=50
CORES_LOWER=1    # Threads lower limit
CORES_UPPER=32   # Threads upper limit
NUM_INIT_VERTICES=(10)
NUM_INIT_SOLUTIONS=(100)
SEED=-1
TIME_LIMIT_MS=(1000 2000 3000)
OBJECTIVE_FUNCTIONS=("conductance" "densesubgraph" "degreeentropy" "triangledensestsubgraph" "labelentropy")

# Map arguments
METAHEURISTIC="$1"
GRAPH_LABEL="$2"
GRAPH_DIR="$3"
PREFIX_LOG_DIR="$4"

# Define graph and log dir
GRAPH_NAME=$(basename "$GRAPH_DIR")
LOG_DIR="$PREFIX_LOG_DIR/scalability/${GRAPH_NAME}"

# Validate graph directory
if [ ! -d "$GRAPH_DIR" ]; then
    echo "Error: Graph directory '$GRAPH_DIR' does not exist"
    exit 1
fi

mkdir -p "$LOG_DIR"
echo "Starting Scalability experiments for $GRAPH_NAME..."

REPEATS=5

# Loop script start
for ((core=$CORES_LOWER; core<=$CORES_UPPER; core++)); do
    for solutions in "${NUM_INIT_SOLUTIONS[@]}"; do
        for vertices in "${NUM_INIT_VERTICES[@]}"; do
            for timeLimit in "${TIME_LIMIT_MS[@]}"; do
                for objFunc in "${OBJECTIVE_FUNCTIONS[@]}"; do
                    for run in $(seq 1 $REPEATS); do

                        if [ "$GRAPH_LABEL" = "unlabeled" ] && [ "$objFunc" = "labelentropy" ]; then
                          # echo "Skipping labelentropy for unlabeled graph (Core $core, Run $run)"
                          continue
                        fi

                        # Build arguments
                        ARGS="$GRAPH_DIR $vertices $solutions $SEED $timeLimit $objFunc $METAHEURISTIC $GRAPH_LABEL"

                        # Base Filename
                        BASENAME="$GRAPH_NAME-$METAHEURISTIC-${core}-${vertices}-${solutions}-${timeLimit}-${objFunc}-${run}"
                        LOG_FILE="$LOG_DIR/${BASENAME}.txt"

                        # Build command (Removed ./gradlew jar from here)
                        EXEC_COMMAND="master_memory=${MEMORY}g app_class=br.ufmg.cs.systems.fractal.apps.SubgraphOptimizationApp worker_cores=${core} args=\"$ARGS\" ./bin/fractal-custom-app.sh"

                        echo "Running Scalability: $BASENAME"

                        # Execute with error handling
                        if eval "$EXEC_COMMAND" > "$LOG_FILE" 2>&1; then
                            # Success
                            gzip -f "$LOG_FILE"
                        else
                            # Failure
                            echo "!!! FAILURE DETECTED: $BASENAME !!!"
                            mv "$LOG_FILE" "$LOG_DIR/${BASENAME}_FAILED.txt"
                        fi

                    done
                done
            done
        done
    done
done