#!/bin/bash

set -e

# Define the variables
memory=50
cores_lower=32       # threads lower limit
cores_upper=32       # threads upper limit
numInitVertices=(10)
numInitSolutions=(100 1000)
seed=(-1)
timeLimitMs=(1000 2000 3000)
objectiveFunction=("conductance" "densesubgraph" "degreeentropy" "labelentropy" "triangledensestsubgraph")
repeats=5 # Number of times to repeat each run

# Define graph name and directory
<<<<<<< HEAD
graphDir="$HOME/graphs-data/citeseer"
graphName="citeseer"
=======
graphDir="$HOME/graphs-data/patents"
graphName="patents"
>>>>>>> ed77dc9 (scripts corrections)

# Loop through all combinations
for ((core=cores_lower; core<=$cores_upper; core++)); do
    for solutions in "${numInitSolutions[@]}"; do
        for vertices in "${numInitVertices[@]}"; do
            for timeLimit in "${timeLimitMs[@]}"; do
                for objFunc in "${objectiveFunction[@]}"; do
                    for run in $(seq 1 $repeats); do

                        # Build the args
                        args="$graphDir $vertices $solutions $seed $timeLimit $objFunc"
                        prefix="$graphName-${core}-${vertices}-${solutions}-${timeLimit}-${objFunc}_${run}"

                        # Build the full command
                        full_command="./gradlew jar && master_memory=${memory}g app_class=br.ufmg.cs.systems.fractal.apps.VNSApp worker_cores=${core} args=\"$args\" ./bin/fractal-custom-app.sh"

                        # Run the main Spark job
                        eval "$full_command" &
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
                        ./scripts/so_metrics_pidstat.sh "$prefix" &
                        pidstat_pid=$!
                        ./scripts/so_metrics_tma.sh "$prefix" &
                        tma_pid=$!

                        # Wait for main Spark process to complete
                        wait $main_pid

                        # Stop monitoring scripts
                        kill $pidstat_pid $tma_pid 2>/dev/null || true

                        echo "Run $prefix completed."
                    done
                done
            done
        done
    done
done

