# Navigate to the fractal-private directory
cd ~/fractal-private || { echo "Error: fractal-private directory not found."; exit 1; }

# Define the graphs array 
graphs=("citeseer" "patents" "youtube")

# List of scripts to run
scripts=("run_optimality.sh" "run_scalability.sh" "run_so_metrics.sh" "run_profiling.sh")

# Loop through each graph in graphs list
for graph in "${graphs[@]}"; do
	echo "--- Processing graph: $graph --"

	# Run each script for the current graph
	for script in "${scripts[@]}"; do
		echo "Running $script for $graph..."
		./scripts/"$script" "$graph" 
		echo "$script finished"
	done
	
	echo "--- Finished processing graph: $graph ---"
done 

echo "All scripts completed successfully for all graphs"
