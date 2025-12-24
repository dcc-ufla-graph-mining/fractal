# Navigate to the fractal-private directory
cd ~/fractal-private || { echo "Error: fractal-private directory not found."; exit 1; }


# Define the graphs
GRAPHS_DIRECTORY="$HOME/graphs-data/"
GRAPHS=("citeseer" "amazon" "dblp" "patents" "livejournal" "youtube")
GRAPHS_LABEL_TYPE=("vertexlabeled" "unlabeled" "unlabeled" "vertexlabeled" "unlabeled" "vertexlabeled" )
METAHEURISTIC = "ils"

# List of scripts to run
SCRIPTS=("run_optimality.sh" "run_scalability.sh" "run_so_metrics.sh" "run_profiling.sh")

# Loop through each graph in graphs list
for graph in "${GRAPHS[@]}"; do
	if [ ! -d "$GRAPHS_DIRECTORY/$graph" ]; then
            echo "Error: Graph directory '$GRAPHS_DIRECTORY/$graph' not found"
            continue  # Skip to next graph
        fi

	echo "--- Processing graph: $graph --"

	# Run each script for the current graph
	for script in "${SCRIPTS[@]}"; do
		echo "Running $script for $graph..."
		./scripts/"$script" "$GRAPHS_LABEL_TYPE" "$GRAPHS_DIRECTORY/$graph" "$METAHEURISTIC"
		echo "$script finished"
	done
	
	echo "--- Finished processing graph: $graph ---"
done 

echo "All scripts completed successfully for all graphs"
