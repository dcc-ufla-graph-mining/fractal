cd ~/fractal-private

#run_optimality_exp.sh run_scalability_exp.sh

for script in run_and_extract_so_metrics.sh run_extract_profiling.sh; do
	./scripts/"$script" 
done 
