# Extracts experiment metrics from .txt and .txt.gz log files in a directory (and subdirectories).
# Usage: python extract_output_metrics.py <directory_path>

import sys
import re
import os
import glob
import gzip
from collections import deque  # Added for reading last N lines

def extract_repetition_number(filename):
    """Extract the repetition number from filename (e.g., '_5.txt.gz' → 5)"""
    match = re.search(r'_(\d+)\.(?:txt|log)(?:\.gz)?$', filename)
    return int(match.group(1)) if match else 0

def extract_experiment_parameters(logfile):
    """Extract experiment parameters from the log file header"""
    params = {
        'graph_name': None,
        'initial_vertices': None,
        'num_initial_solutions': None,
        'timeout_ms': None,
        'objective_function': None,
        'num_threads': None,
        'repetition': extract_repetition_number(logfile)
    }

    open_func = gzip.open if logfile.endswith('.gz') else open
    mode = 'rt' if logfile.endswith('.gz') else 'r'

    with open_func(logfile, mode) as f:
        for line in f:
            if "args is set to '" in line:
                args_match = re.search(r"args is set to '([^']+)'", line)
                if args_match:
                    args = args_match.group(1).split()
                    if len(args) >= 6:
                        params['graph_name'] = os.path.basename(args[0].rstrip('/'))
                        params['initial_vertices'] = int(args[1])
                        params['num_initial_solutions'] = int(args[2])
                        params['timeout_ms'] = int(args[4])
                        params['objective_function'] = args[5]

            if "--executor-cores" in line:
                threads_match = re.search(r"--executor-cores\s+(\d+)", line)
                if threads_match:
                    params['num_threads'] = int(threads_match.group(1))

    return params

def process_log_file(logfile):
    """Process a single log file to extract experiment results"""
    params = extract_experiment_parameters(logfile)
    best_subgraph = None
    total_time_ms = None
    effective_runs = None

    READ_ONLY_LAST_LINES = True  # ✅ Toggle: set to False to read entire file

    open_func = gzip.open if logfile.endswith('.gz') else open
    mode = 'rt' if logfile.endswith('.gz') else 'r'

    if READ_ONLY_LAST_LINES:
        # ✅ Optimized: read only last 5 lines
        with open_func(logfile, mode) as f:
            last_lines = deque(f, maxlen=5)

        last_lines = list(last_lines)
        if len(last_lines) >= 2:
            penultimate_line = last_lines[-2].strip()
            last_line = last_lines[-1].strip()

            # Extract effective runs
            match = re.search(r'VNSSubgraphOptimization\s+(\d+)', penultimate_line)
            if match:
                effective_runs = int(match.group(1)) + 1

            # Extract best subgraph info
            if "BestSubgraph" in last_line:
                time_match = re.search(r'ElapsedTimeMs=(\d+)', last_line)
                if time_match:
                    total_time_ms = int(time_match.group(1))
                best_subgraph = {
                    'line': last_line.split('BestSubgraph=')[1].strip(),
                    'nvertices': int(re.search(r'nvertices=(\d+)', last_line).group(1)),
                    'nedges': int(re.search(r'nedges=(\d+)', last_line).group(1)),
                    'cost': float(re.search(r'cost=([\d.Ee+-]+)', last_line).group(1))  # Handles scientific notation
                }

    # else:
    # 🔁 Fallback: full file reading (uncomment if needed)
    # with open_func(logfile, mode) as f:
    #     for line in f:
    #         line = line.strip()
    #         if "VNSSubgraphOptimization" in line:
    #             parts = re.split(r'\s+', line)
    #             last_run_number = int(parts[3])
    #         elif "BestSubgraph" in line:
    #             time_match = re.search(r'ElapsedTimeMs=(\d+)', line)
    #             if time_match:
    #                 total_time_ms = int(time_match.group(1))
    #             best_subgraph = {
    #                 'line': line.split('BestSubgraph=')[1].strip(),
    #                 'nvertices': int(re.search(r'nvertices=(\d+)', line).group(1)),
    #                 'nedges': int(re.search(r'nedges=(\d+)', line).group(1)),
    #                 'cost': float(re.search(r'cost=([\d.Ee+-]+)', line).group(1))
    #             }
    #     effective_runs = (last_run_number + 1) if last_run_number is not None else 0

    return {
        **params,
        'total_time_ms': total_time_ms,
        'effective_runs': effective_runs if effective_runs is not None else 0,
        'cost_best_solution': best_subgraph['cost'] if best_subgraph else None,
        'vertices_best_solution': best_subgraph['nvertices'] if best_subgraph else None,
        'edges_best_solution': best_subgraph['nedges'] if best_subgraph else None,
        'best_solution': f'"{best_subgraph["line"]}"' if best_subgraph else None
    }

def process_directory(directory_path):
    """Process all .txt and .txt.gz files in a directory"""
    all_results = []
    for filepath in glob.glob(os.path.join(directory_path, '**', '*.txt*'), recursive=True):
        if filepath.endswith(('.txt', '.txt.gz')):
            try:
                print(f"Processing {filepath}...")
                all_results.append(process_log_file(filepath))
            except Exception as e:
                print(f"Error processing {filepath}: {str(e)}")
                continue

    if not all_results:
        print("No valid log files found in directory")
        return

    output_file = "experiments_results.csv"
    fields = [
        ('graph_name', 'Graph Name'),
        ('initial_vertices', 'Initial Vertices'),
        ('num_initial_solutions', 'Initial Solutions'),
        ('timeout_ms', 'Timeout (ms)'),
        ('objective_function', 'Objective Function'),
        ('num_threads', 'Threads'),
        ('repetition', 'Repetition'),
        ('total_time_ms', 'Total Time (ms)'),
        ('effective_runs', 'Effective Runs'),
        ('cost_best_solution', 'Cost Best Solution'),
        ('vertices_best_solution', 'Vertices Best Solution'),
        ('edges_best_solution', 'Edges Best Solution'),
        ('best_solution', 'Best Solution')
    ]

    file_exists = os.path.isfile(output_file)

    with open(output_file, 'a' if file_exists else 'w') as f:
        if not file_exists:
            f.write(','.join([header for _, header in fields]) + '\n')

        for result in all_results:
            row = [str(result.get(field, '')) for field, _ in fields]
            f.write(','.join(row) + '\n')

    print(f"\nResults {'appended to' if file_exists else 'saved to'}: {os.path.abspath(output_file)}")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: python extract_experiment_results.py <directory_path>")
        sys.exit(1)

    directory_path = sys.argv[1]
    if not os.path.isdir(directory_path):
        print(f"Error: {directory_path} is not a valid directory")
        sys.exit(1)

    process_directory(directory_path)
