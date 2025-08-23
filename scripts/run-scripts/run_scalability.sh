#!/bin/bash

set -e

# Usage: ./run_scalability.sh [graph_label_type] [graph_directory]

# Configuration
MEMORY=50
CORES_LOWER=1		# Threads lower limit
CORES_UPPER=32		# Threads upper limit
NUM_INIT_VERTICES=(10)
NUM_INIT_SOLUTIONS=(100)
SEED=-1
TIME_LIMIT_MS=(1000 2000 3000)
OBJECTIVE_FUNCTIONS=("conductance" "densesubgraph" "degreeentropy" "triangledensestsubgraph" "labelentropy")
GRAPH_LABEL="$1"

REPEATS=5 # Number of times to repeat the experiment for each combination of variables 

# Define graph and log dir
GRAPH_DIR="$2"
GRAPH_NAME=$(basename "$GRAPH_DIR")      # Extract last directory name
LOG_DIR="optimization-logs/scalability/${GRAPH_NAME}"

# Validate graph directory exists
if [ ! -d "$GRAPH_DIR" ]; then
    echo "Error: Graph directory '$GRAPH_DIR' does not exist"
    exit 1
fi

# Create log directory
mkdir -p "$LOG_DIR"


# Loop script start
for ((core=$CORES_LOWER; core<=$CORES_UPPER; core++)); do
    for solutions in "${NUM_INIT_SOLUTIONS[@]}"; do
        for vertices in "${NUM_INIT_VERTICES[@]}"; do
            for timeLimit in "${TIME_LIMIT_MS[@]}"; do
                for objFunc in "${OBJECTIVE_FUNCTIONS[@]}"; do
                    for run in $(seq 1 $REPEATS); do

                        # Build the arguments and log filename
                        ARGS="$GRAPH_DIR $vertices $solutions $SEED $timeLimit $objFunc $GRAPH_LABEL"
                        LOG_FILE="$LOG_DIR/$GRAPH_NAME-${core}-${vertices}-${solutions}-${timeLimit}-${objFunc}-$GRAPH_LABEL-${run}.txt"

                        # Build the full command
                        EXEC_COMMAND="./gradlew jar && master_memory=${MEMORY}g app_class=br.ufmg.cs.systems.fractal.apps.VNSApp worker_cores=${core} args=\"$ARGS\" ./bin/fractal-custom-app.sh"

                        # Show the command being run
                        echo "$EXEC_COMMAND"

                        # Execute the command
                        eval "$EXEC_COMMAND" > "$LOG_FILE" 2>&1

                        # Gzip the log file
                        gzip "$LOG_FILE" && echo "Compressed: $LOG_FILE.gz"

                    done
                done
            done
        done
    done
done
