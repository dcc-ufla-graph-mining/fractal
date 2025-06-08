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

# Define graph and log dir
graphDir="$HOME/graphs-data/dblp"
graphName="dblp"
logDir="$HOME/optimization-logs/optimality/temp/dblp"

mkdir -p "$logDir"

# Loop through all combinations
for ((core=cores_lower; core<=$cores_upper; core++)); do
    for solutions in "${numInitSolutions[@]}"; do
        for vertices in "${numInitVertices[@]}"; do
            for timeLimit in "${timeLimitMs[@]}"; do
                for objFunc in "${objectiveFunction[@]}"; do
                    for run in $(seq 1 $repeats); do

                        # Build the args
                        args="$graphDir $vertices $solutions $seed $timeLimit $objFunc"
                        log_file="$logDir/$graphName-${core}-${vertices}-${solutions}-${timeLimit}-${objFunc}_${run}.txt"

                        # Build the full command
                        full_command="./gradlew jar && master_memory=${memory}g app_class=br.ufmg.cs.systems.fractal.apps.VNSApp worker_cores=${core} args=\"$args\" ./bin/fractal-custom-app.sh 2>&1 | tee \"$log_file\"  "

                        # Show the command being run
                        echo "$full_command"

                        # Execute the command
                        eval "$full_command"

                    done
                done
            done
        done
    done
done
