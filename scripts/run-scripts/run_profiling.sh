#!/bin/bash

set -e

# Usage: ./run_profiling.sh [graph_label_type] [graph_directory]

# Configuration
MEMORY=50
CORES=32
NUM_INIT_VERTICES=(10)
NUM_INIT_SOLUTIONS=(100 1000)
SEED=-1
TIME_LIMIT_MS=(1000 2000 3000)
OBJECTIVE_FUNCTIONS=("conductance" "densesubgraph" "degreeentropy" "triangledensestsubgraph" "labelentropy")
GRAPH_LABEL="$1"
METAHEURISTIC="$3"

REPEATS=5 # Number of times to repeat the experiment for each combination of variables 

# Define graph and log dir
GRAPH_DIR="$2"
GRAPH_NAME=$(basename "$GRAPH_DIR")	# Extract last directory name
LOG_DIR="optimization-logs/ccpe2026/profiling/$METAHEURISTIC/${GRAPH_NAME}"

# Validate graph directory exists
if [ ! -d "$GRAPH_DIR" ]; then
    echo "Error: Graph directory '$GRAPH_DIR' does not exist"
    exit 1
fi

# Create log directory
mkdir -p "$LOG_DIR"

# Loop script start
for solutions in "${NUM_INIT_SOLUTIONS[@]}"; do
    for vertices in "${NUM_INIT_VERTICES[@]}"; do
        for timeLimit in "${TIME_LIMIT_MS[@]}"; do
            for objFunc in "${OBJECTIVE_FUNCTIONS[@]}"; do
                for run in $(seq 1 $REPEATS); do
                    # Skip labelentropy for unlabeled graphs
                    if [ "$GRAPH_LABEL" = "unlabeled" ] && [ "$objFunc" = "labelentropy" ]; then
                        echo "Skipping labelentropy for unlabeled graph"
                        continue
                    fi

                    # Build the arguments and log filename
                    ARGS="$GRAPH_DIR $vertices $solutions $SEED $timeLimit $objFunc $METAHEURISTIC $GRAPH_LABEL"
                    LOG_FILE="$LOG_DIR/$GRAPH_NAME-$METAHEURISTIC-${vertices}-${solutions}-${timeLimit}-${objFunc}-${run}.txt"

                    # Build the full command
                    EXEC_COMMAND="./gradlew jar && master_memory=${MEMORY}g app_class=br.ufmg.cs.systems.fractal.apps.SubgraphOptimizationApp worker_cores=${CORES} event=cpu file=\"$LOG_DIR/$LOG_FILE\"  args=\"$ARGS\" ./bin/fractal-custom-app-profiling.sh"

                    # Show the command being run
                    echo "$EXEC_COMMAND"

                    # Execute the command
                    eval "$EXEC_COMMAND"

           	    # Gzip the log file
                    gzip "$LOG_FILE" && echo "Compressed: $LOG_FILE.gz"

                done
            done
        done
    done
done
