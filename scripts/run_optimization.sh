#!/bin/bash

# Crash the script immediately if anything fails
set -e 

# Define the variables
memory=50 
cores=32
graphDir="$HOME/graphs-data/citeseer"
graphName="citeseer"
logDir="$HOME/optimization-logs/citeseer-logs"
numInitVertices=(10)
numInitSolutions=(10 100 1000)
seed=(-1)
timeLimitMs=(1000 2000 3000) 
objectiveFunction=("densitymass" "conductance" "modularity" "densesubgraph" "triangledensestsubgraph")

# Number of times to repeat each run
repeats=5

# First part of the command 
base_command="./gradlew jar && master_memory=${memory}g worker_cores=$cores app_class=br.ufmg.cs.systems.fractal.apps.VNSApp"

# Make sure the log directory exists
mkdir -p "$logDir"

# Loop through all combinations
for vertices in "${numInitVertices[@]}"; do
  for solutions in "${numInitSolutions[@]}"; do
    for timeLimit in "${timeLimitMs[@]}"; do
      for objFunc in "${objectiveFunction[@]}"; do
        for run in $(seq 1 $repeats); do
          # Build the args
          args="$graphDir $vertices $solutions $seed $timeLimit $objFunc"
          log_file="$logDir/$graphName-${vertices}-${solutions}-${timeLimit}-${objFunc}_${run}.txt"

	  # Build full command
	  full_command="$base_command args=\"$args\" ./bin/fractal-custom-app.sh 2>&1 | tee \"$log_file\""

	  # Show the command being run
	  echo "$full_command"

	  # Execute the command
	  eval "$full_command"
        done
      done
    done
  done
done
