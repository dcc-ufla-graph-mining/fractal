#!/bin/bash

# Usage: ./run_so_metrics.sh [graph_label_type] [graph_directory]

# Configuration
MEMORY=50
CORES=32
NUM_INIT_VERTICES=(10)
NUM_INIT_SOLUTIONS=(100 1000)
SEED=-1
TIME_LIMIT_MS=(1000 2000 3000)
OBJECTIVE_FUNCTIONS=("conductance" "densesubgraph" "degreeentropy" "triangledensestsubgraph" "labelentropy")
GRAPH_LABEL="$1"

REPEATS=5 # Number of times to repeat the experiment for each combination of variables 

# Define graph and log dir
GRAPH_DIR="$2"
GRAPH_NAME=$(basename "$GRAPH_DIR")	# Extract last directory name
LOG_DIR="optimization-logs/performance-metrics/${GRAPH_NAME}"

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

                    # Build the arguments and log filename
                    ARGS="$GRAPH_DIR $vertices $solutions $SEED $timeLimit $objFunc $GRAPH_LABEL"
        	    PREFIX="$GRAPH_NAME-$CORES-${vertices}-${solutions}-${timeLimit}-${objFunc}-$GRAPH_LABEL-${run}"

                    LOG_FILE="$LOG_DIR/$GRAPH_NAME-$CORES-${vertices}-${solutions}-${timeLimit}-${objFunc}-$GRAPH_LABEL-${run}.txt"

                    # Build the full command
                    EXEC_COMMAND="./gradlew jar && master_memory=${MEMORY}g app_class=br.ufmg.cs.systems.fractal.apps.VNSApp worker_cores=${CORES} args=\"$ARGS\" ./bin/fractal-custom-app.sh"

                    # Run the main Spark job
                    eval "$EXEC_COMMAND" > /dev/null 2>&1  &
                    main_pid=$!

                    # Wait for Spark process to start
                    echo "Waiting for Spark process to start..."
                    spark_pid=""
                    timeout=30
                    while [ $timeout -gt 0 ] && [ -z "$spark_pid" ]; do
                        spark_pid=$(pgrep -f 'java.*SparkSubmit' | head -n 1)
                        sleep 1
                        ((timeout--))
                    done

                    if [ -z "$spark_pid" ]; then
                        echo "Error: Spark process did not start within 30 seconds. Run $prefix skipped"
                        kill $main_pid 2>/dev/null || true
                        continue
                    fi

                    echo "Spark process found with PID $spark_pid"

                    # Start monitoring scripts
                    ./scripts/run-scripts/pid_stat.sh "$PREFIX" "$LOG_DIR/pid-stats" &
                    pidstat_pid=$!
                    ./scripts/run-scripts/tma_metrics.sh "$PREFIX" "$LOG_DIR/tma-metrics" &
                    tma_pid=$!

                    # Wait for main Spark process to complete
                    wait $main_pid

                    # Stop monitoring scripts
                    kill $pidstat_pid $tma_pid 2>/dev/null || true

                    echo "Run $PREFIX completed."

                done
            done
        done
    done
done


# Show the command being run
                    echo "$EXEC_COMMAND"

                    # Execute the command
                    eval "$EXEC_COMMAND" > "$LOG_FILE" 2>&1

                    # Gzip the log file
                    gzip "$LOG_FILE" && echo "Compressed: $LOG_FILE.gz"
