#!/bin/bash

<<<<<<< HEAD
=======
# Crash the script immediately if anything fails
>>>>>>> ed77dc9 (scripts corrections)
set -e

# Define the variables
memory=50
<<<<<<< HEAD
cores_lower=32       # threads lower limit
cores_upper=32       # threads upper limit
numInitVertices=(10)
numInitSolutions=(100 1000)
seed=(-1)
timeLimitMs=(1000 2000 3000)
objectiveFunction=("conductance" "densesubgraph" "triangledensestsubgraph" "degreeentropy" "labelentropy")
repeats=5 # Number of times to repeat each run
=======
c=32    # cores lower limit
cores=32  # cores upper limit
graphDir="$HOME/graphs-data/dblp"
graphName="dblp"
logDir="$HOME/optimization-logs/optimality/temp/dblp"
numInitVertices=(10)
numInitSolutions=(10000)
seed=(-1)
timeLimitMs=(1000 2000 3000)
objectiveFunction=("conductance" "densesubgraph" "degreeentropy" "labelentropy" "triangledensestsubgraph")
>>>>>>> ed77dc9 (scripts corrections)

# Define graph and log dir
graphDir="$HOME/graphs-data/youtube"
graphName="youtube"
logDir="$HOME/optimization-logs/youtube-logs"

mkdir -p "$logDir"

base_command="./gradlew jar && master_memory=${memory}g app_class=br.ufmg.cs.systems.fractal.apps.VNSApp"

# Loop through all combinations
<<<<<<< HEAD
for ((core=cores_lower; core<=$cores_upper; core++)); do
    for solutions in "${numInitSolutions[@]}"; do
        for vertices in "${numInitVertices[@]}"; do
            for timeLimit in "${timeLimitMs[@]}"; do
                for objFunc in "${objectiveFunction[@]}"; do
                    for run in $(seq 1 $repeats); do

                        # Build the args
                        args="$graphDir $vertices $solutions $seed $timeLimit $objFunc"
                        log_file="$logDir/$graphName-${core}-${vertices}-${solutions}-${timeLimit}-${objFunc}_${run}.txt.gz"

                        # Build the full command
                        full_command="./gradlew jar && master_memory=${memory}g app_class=br.ufmg.cs.systems.fractal.apps.VNSApp worker_cores=${core} args=\"$args\" ./bin/fractal-custom-app.sh 2>&1 | tee \"$log_file\"  "

                        # Show the command being run
                        echo "$full_command"

                        # Execute the command
                        eval "$full_command | gzip > "$log_file""

=======
for ((core=c; core<=$cores; core++)); do
    for vertices in "${numInitVertices[@]}"; do
        for solutions in "${numInitSolutions[@]}"; do
            for timeLimit in "${timeLimitMs[@]}"; do
                for objFunc in "${objectiveFunction[@]}"; do
                    for run in $(seq 1 $repeats); do
                        # Build the args
                        args="$graphDir $vertices $solutions $seed $timeLimit $objFunc"
                        log_file="$logDir/$graphName-${core}-${vertices}-${solutions}-${timeLimit}-${objFunc}_${run}.txt"

                        # Build the full command
                        full_command="$base_command worker_cores=${core} args=\"$args\" ./bin/fractal-custom-app.sh 2>&1 | tee \"$log_file\""
			
			# Show the command being run
			echo "$full_command"

			# Execute the command
			eval "$full_command"

>>>>>>> ed77dc9 (scripts corrections)
                    done
                done
            done
        done
    done
done
