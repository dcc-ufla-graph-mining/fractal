import sys
import re
import os
import glob
import gzip
from collections import deque
from datetime import datetime

def extract_repetition_number(filename):
    match = re.search(r'_(\d+)\.(?:txt|log)(?:\.gz)?$', filename)
    return int(match.group(1)) if match else 0

def extract_experiment_parameters(logfile):
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
    mode = 'rt'

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

def parse_timestamp(line):
    match = re.match(r"(\d{2}/\d{2}/\d{2} \d{2}:\d{2}:\d{2})", line)
    if match:
        try:
            return datetime.strptime(match.group(1), "%d/%m/%y %H:%M:%S")
        except Exception as e:
            print(f"Failed to parse timestamp: {match.group(1)} – {e}")
    return None

def process_log_file(logfile):
    print(f"\n--- Processing file: {logfile} ---")
    params = extract_experiment_parameters(logfile)
    best_subgraph = None
    total_time_ms = None
    effective_runs = None
    time_to_best_ms = None

    open_func = gzip.open if logfile.endswith('.gz') else open
    mode = 'rt'

    with open_func(logfile, mode) as f:
        lines = list(f)

    last_lines = deque(lines, maxlen=5)
    last_lines = list(last_lines)
    if len(last_lines) >= 2:
        penultimate_line = last_lines[-2].strip()
        last_line = last_lines[-1].strip()

        match = re.search(r'VNSSubgraphOptimization\s+(\d+)', penultimate_line)
        if match:
            effective_runs = int(match.group(1)) + 1

        if "BestSubgraph" in last_line or "VNSSubgraphOptimization" in last_line:
            time_match = re.search(r'ElapsedTimeMs=(\d+)', last_line)
            if time_match:
                total_time_ms = int(time_match.group(1))

            cost_match = re.search(r'cost=([\d.]+)', last_line) or re.search(r'(\d+\.\d+)$', last_line)
            if cost_match:
                best_cost = float(cost_match.group(1))
                best_subgraph = {
                    'line': last_line.split('BestSubgraph=')[1].strip() if "BestSubgraph" in last_line else last_line,
                    'nvertices': int(re.search(r'nvertices=(\d+)', last_line).group(1)) if "nvertices" in last_line else 0,
                    'nedges': int(re.search(r'nedges=(\d+)', last_line).group(1)) if "nedges" in last_line else 0,
                    'cost': best_cost
                }

    first_vns_time = None
    best_cost_time = None

    for line in lines:
        if 'VNSSubgraphOptimization' in line:
            timestamp = parse_timestamp(line)

            if not first_vns_time:
                first_vns_time = timestamp
                print(f"DEBUG 1/4: Initial time extracted: {first_vns_time}")

            if best_subgraph and 'cost' in best_subgraph:
                cost_match = re.search(r'cost=([\d.]+)', line) or re.search(r'(\d+\.\d+)$', line)
                if cost_match and abs(float(cost_match.group(1)) - best_subgraph['cost']) < 1e-9:
                    best_cost_time = timestamp
                    print(f"DEBUG 2/4: First best cost appearance: {best_cost_time}")
                    break

    print(f"DEBUG 3/4: Best cost time is: {best_cost_time}")

    if first_vns_time and best_cost_time:
        delta = best_cost_time - first_vns_time
        time_to_best_ms = int(delta.total_seconds() * 1000)
        print(f"DEBUG 4/4: Time difference: {time_to_best_ms}ms (from {first_vns_time} to {best_cost_time})")
    else:
        print("DEBUG 4/4: Missing timestamps for calculation")

    return {
        **params,
        'total_time_ms': total_time_ms,
        'time_to_best_solution_ms': time_to_best_ms,
        'effective_runs': effective_runs if effective_runs is not None else 0,
        'cost_best_solution': best_subgraph['cost'] if best_subgraph else None,
        'vertices_best_solution': best_subgraph['nvertices'] if best_subgraph else None,
        'edges_best_solution': best_subgraph['nedges'] if best_subgraph else None,
        'best_solution': f'"{best_subgraph["line"]}"' if best_subgraph else None
    }

def process_directory(directory_path):
    all_results = []
    for filepath in glob.glob(os.path.join(directory_path, '**', '*.txt*'), recursive=True):
        if filepath.endswith(('.txt', '.txt.gz')):
            try:
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
        ('time_to_best_solution_ms', 'Time to Best Solution (ms)'),
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
        print("Usage: python extract_output_metrics.py <directory_path>")
        sys.exit(1)

    directory_path = sys.argv[1]
    if not os.path.isdir(directory_path):
        print(f"Error: {directory_path} is not a valid directory")
        sys.exit(1)

    process_directory(directory_path)
