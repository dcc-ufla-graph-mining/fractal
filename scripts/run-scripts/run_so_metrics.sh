#!/bin/bash

# Usage: ./run_so_metrics.sh [metaheuristic] [graph_label_type] [graph_directory] [prefix_log_dir]

# 1. Check Arguments
if [ $# -ne 4 ]; then
    echo "Usage: $0 [metaheuristic] [graph_label_type] [graph_directory] [prefix_log_dir]"
    exit 1
fi

# 2. Build ONCE at the start
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

# Input Arguments
METAHEURISTIC="$1"
GRAPH_LABEL="$2"
GRAPH_DIR="$3"
PREFIX_LOG_DIR="$4"

REPEATS=5

if [ ! -d "$GRAPH_DIR" ]; then
    echo "Error: Graph directory '$GRAPH_DIR' does not exist"
    exit 1
fi

GRAPH_NAME=$(basename "$GRAPH_DIR")
LOG_DIR="$PREFIX_LOG_DIR/performance-metrics/${GRAPH_NAME}"
mkdir -p "$LOG_DIR"

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
                    PREFIX="$GRAPH_NAME-$METAHEURISTIC-$CORES-${vertices}-${solutions}-${timeLimit}-${objFunc}-${run}"
                    LOG_FILE="$LOG_DIR/${PREFIX}.txt"

                    echo "Running Performance Metrics: $PREFIX"

                    # Build Command
                    EXEC_COMMAND="master_memory=${MEMORY}g app_class=br.ufmg.cs.systems.fractal.apps.SubgraphOptimizationApp worker_cores=${CORES} args=\"$ARGS\" ./bin/fractal-custom-app.sh"

                    # Run Spark Job in Background
                    eval "$EXEC_COMMAND" > "$LOG_FILE" 2>&1 &
                    main_shell_pid=$!

                    # Wait for Spark Java process to appear
                    spark_pid=""
                    timeout=30
                    while [ $timeout -gt 0 ]; do
                        # Check if the main wrapper script already died (e.g., config error)
                        if ! kill -0 "$main_shell_pid" 2>/dev/null; then
                            echo "  !! Error: Main wrapper script died immediately."
                            break
                        fi

                        # Look for SparkSubmit belonging to THIS USER
                        spark_pid=$(pgrep -u "$USER" -f 'java.*SparkSubmit' | sort -n | tail -n 1)

                        if [ -n "$spark_pid" ] && kill -0 "$spark_pid" 2>/dev/null; then
                            break
                        fi
                        sleep 1
                        ((timeout--))
                    done

                    # ERROR HANDLING: Process didn't start or wrapper died
                    if [ -z "$spark_pid" ] || ! kill -0 "$spark_pid" 2>/dev/null; then
                        echo "  !! Error: Spark process did not start or was not found."
                        # Ensure wrapper is dead
                        kill $main_shell_pid 2>/dev/null

                        # Rename log
                        mv "$LOG_FILE" "${LOG_DIR}/${PREFIX}_FAILED_STARTUP.txt"
                        echo "Startup timed out or failed immediately." >> "${LOG_DIR}/${PREFIX}_FAILED_STARTUP.txt"
                        continue
                    fi

                    echo "  -> Spark Java PID found: $spark_pid"

                    # Start monitoring scripts
                    ./scripts/run-scripts/pid_stat.sh "$PREFIX" "$LOG_DIR/pid-stats" "$spark_pid" &
                    monitor_pid_1=$!

                    ./scripts/run-scripts/tma_metrics.sh "$PREFIX" "$LOG_DIR/tma-metrics" "$spark_pid" &
                    monitor_pid_2=$!

                    # Wait for the main job to finish and CAPTURE EXIT CODE
                    wait $main_shell_pid
                    EXIT_CODE=$?

                    # Wait for monitors to finish
                    wait $monitor_pid_1
                    wait $monitor_pid_2

                    # ERROR HANDLING: Check Exit Code
                    if [ $EXIT_CODE -eq 0 ]; then
                        echo "  -> Run Success. Removing app log (keeping metrics only)..."
                        rm "$LOG_FILE"  # <--- Deletes the main log
                    else
                        echo "  !! Run FAILED (Exit Code: $EXIT_CODE)."
                        mv "$LOG_FILE" "${LOG_DIR}/${PREFIX}_FAILED.txt"
                        {
                            echo ""
                            echo "########################################"
                            echo "SYSTEM ERROR: Process exited with code $EXIT_CODE"
                            echo "########################################"
                        } >> "${LOG_DIR}/${PREFIX}_FAILED.txt"
                    fi

                    echo "------------------------------------------------"

                done
            done
        done
    done
done