#!/bin/bash

# Navigate to the fractal directory
cd ~/fractal || { echo "Error: fractal directory not found."; exit 1; }


# Define graph to label mapping
declare -A GRAPH_LABELS=(
    ["citeseer"]="vertexlabeled"
    ["amazon"]="unlabeled"
    ["dblp"]="unlabeled"
    ["patents"]="vertexlabeled"
    ["livejournal"]="unlabeled"
    ["youtube"]="vertexlabeled"
)

GRAPHS_DIRECTORY="$HOME/graphs-data/"
METAHEURISTIC="ils"
PREFIX="optimization-logs/ccpe2026/$METAHEURISTIC"
SCRIPTS=("run_optimality.sh" "run_scalability.sh" "run_so_metrics.sh" "run_profiling.sh")

# Loop through each graph
for graph in "${!GRAPH_LABELS[@]}"; do
    label="${GRAPH_LABELS[$graph]}"
    
    if [ ! -d "$GRAPHS_DIRECTORY/$graph" ]; then
        echo "Error: Graph directory '$GRAPHS_DIRECTORY/$graph' not found"
        continue
    fi

    echo "--- Processing graph: $graph (label type: $label) ---"

    for script in "${SCRIPTS[@]}"; do
        echo "Running $script for $graph..."
        ./scripts/run-scripts/"$script" "$METAHEURISTIC" "$label" "$GRAPHS_DIRECTORY/$graph" "$PREFIX"
        echo "$script finished"
    done

    echo "--- Finished processing graph: $graph ---"
done

echo "Scripts completed for all graphs"
