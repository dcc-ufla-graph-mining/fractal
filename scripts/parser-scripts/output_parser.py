#!/usr/bin/env python3
# Extracts experiment metrics from log files and saves them to a CSV.
# Usage: python extract_output_metrics.py <directory_path>

import sys
import re
import os
import glob
import gzip
from collections import deque
from datetime import datetime

def extract_repetition_number(filename):
    """Extracts experiment repetition number from filename."""
    match = re.search(r'-(\d+)(?:\.(?:txt|log)(?:\.gz)?)?$', os.path.basename(filename))
    return int(match.group(1)) if match else 0

def extract_parameters_from_filename(filename):
    """Extracts experiment parameters from the new filename format."""
    basename = os.path.basename(filename)
    # Remove file extension if present
    basename = re.sub(r'\.(?:txt|log)(?:\.gz)?$', '', basename)

    parts = basename.split('-')

    if len(parts) >= 8:
        # Filename format: GRAPH_NAME-METAHEURISTIC-CORES-vertices-solutions-timeLimit-objFunc-run
        return {
            'graph_name': parts[0],
            'metaheuristic': parts[1],
            'num_threads': int(parts[2]),
            'initial_vertices': int(parts[3]),
            'num_initial_solutions': parts[4],
            'timeout_ms': int(parts[5]),
            'objective_function': parts[6],
            'repetition': int(parts[7]) if len(parts) > 7 else 0
        }
    else:
        # Fallback to old format or extraction from file content
        return None

def extract_experiment_parameters(logfile):
    """Extracts experiment parameters from log file headers and filename."""
    # First try to extract from filename (new format)
    filename_params = extract_parameters_from_filename(logfile)

    # Initialize params with filename values or defaults
    if filename_params:
        params = filename_params
    else:
        params = {
            'graph_name': None,
            'metaheuristic': None,
            'initial_vertices': None,
            'num_initial_solutions': None,
            'timeout_ms': None,
            'objective_function': None,
            'num_threads': None,
            'repetition': extract_repetition_number(logfile)
        }

    # Handle both regular and gzipped files
    open_func = gzip.open if logfile.endswith('.gz') else open
    mode = 'rt'

    try:
        with open_func(logfile, mode) as f:
            for line in f:
                # Extract command line arguments (fallback if filename extraction failed)
                if not filename_params and "args is set to '" in line:
                    args_match = re.search(r"args is set to '([^']+)'", line)
                    if args_match:
                        args = args_match.group(1).split()
                        if len(args) >= 6:
                            params['graph_name'] = os.path.basename(args[0].rstrip('/'))
                            params['initial_vertices'] = int(args[1])
                            params['num_initial_solutions'] = args[2]
                            params['timeout_ms'] = int(args[4])
                            params['objective_function'] = args[5]

                # Extract thread count (fallback)
                if not filename_params and "--executor-cores" in line:
                    threads_match = re.search(r"--executor-cores\s+(\d+)", line)
                    if threads_match:
                        params['num_threads'] = int(threads_match.group(1))

                # Try to detect metaheuristic from log content if not from filename
                if not params.get('metaheuristic'):
                    meta_match = re.search(r'(VariableNeighborhoodSearch|IteratedLocalSearch|TabuSearch)', line)
                    if meta_match:
                        params['metaheuristic'] = meta_match.group(1)
    except Exception as e:
        print(f"Warning: Could not read {logfile} for parameter extraction: {e}")

    return params

def parse_timestamp(line, current_date=None):
    """Parses timestamp from log line (format: DD/MM/YY HH:MM:SS) with date handling."""
    match = re.match(r"(\d{2}/\d{2}/\d{2} \d{2}:\d{2}:\d{2})", line)
    if match:
        try:
            parsed_time = datetime.strptime(match.group(1), "%d/%m/%y %H:%M:%S")
            if current_date:
                # Combine with the current date context
                return datetime.combine(current_date, parsed_time.time())
            return parsed_time
        except ValueError:
            return None
    return None

def process_log_file(logfile):
    """Processes a single log file and extracts key metrics."""
    params = extract_experiment_parameters(logfile)
    best_subgraph = None
    total_time_ms = None
    time_to_best_ms = None

    # Read all lines at once for efficient processing
    open_func = gzip.open if logfile.endswith('.gz') else open
    mode = 'rt'
    with open_func(logfile, mode) as f:
        lines = list(f)

    # Extract metrics from the last few lines
    last_lines = deque(lines, maxlen=100)
    best_subgraph_line = None

    # Search all last lines for "BestSubgraph"
    for line in reversed(last_lines):
        line = line.strip()
        if "BestSubgraph" in line:
            best_subgraph_line = line
            break

    # Extract best solution metrics
    if best_subgraph_line:
        time_match = re.search(r'ElapsedTimeMs=(\d+)', best_subgraph_line)
        if time_match:
            total_time_ms = int(time_match.group(1))

        cost_match = re.search(r'cost=([\d.]+)', best_subgraph_line) or re.search(r'(\d+\.\d+)$', best_subgraph_line)
        if cost_match:
            best_cost = float(cost_match.group(1))
            best_subgraph = {
                'line': best_subgraph_line.split('BestSubgraph=')[1].strip() if "BestSubgraph" in best_subgraph_line else best_subgraph_line,
                'nvertices': int(re.search(r'nvertices=(\d+)', best_subgraph_line).group(1)) if "nvertices" in best_subgraph_line else -1,
                'nedges': int(re.search(r'nedges=(\d+)', best_subgraph_line).group(1)) if "nedges" in best_subgraph_line else -1,
                'cost': best_cost
            }

    # Calculate time to the best solution and the number of effective runs
    first_meta_time = None
    best_cost_time = None
    effective_runs = -1
    best_cost_found = False
    current_date = None  # Track the current date context

    # Define metaheuristic patterns
    meta_patterns = [
        'VariableNeighborhoodSearch',
        'IteratedLocalSearch',
        'TabuSearch'
    ]

    # Create regex pattern to match any metaheuristic
    meta_regex = re.compile(r'(' + '|'.join(meta_patterns) + r')\s+(\d+)')

    for line in lines:
        # Update current_date context if timestamp is found
        timestamp = parse_timestamp(line)
        if timestamp:
            if not current_date or timestamp.date() != current_date:
                current_date = timestamp.date()

        # Check for any metaheuristic run
        meta_match = meta_regex.search(line)
        if meta_match:
            # Extract and update the highest run number
            current_run = int(meta_match.group(2))
            if current_run > effective_runs:
                effective_runs = current_run

            if not best_cost_found:
                # Record first metaheuristic timestamp with date context
                if not first_meta_time:
                    first_meta_time = parse_timestamp(line, current_date)

                # Find first occurrence of best cost
                if best_subgraph and 'cost' in best_subgraph:
                    cost_match = re.search(r'cost=([\d.]+)', line) or re.search(r'(\d+\.\d+)$', line)
                    if cost_match and abs(float(cost_match.group(1)) - best_subgraph['cost']) < 1e-9:
                        best_cost_time = parse_timestamp(line, current_date)
                        best_cost_found = True

    effective_runs += 1

    # Calculate time difference if both timestamps were found
    if first_meta_time and best_cost_time:
        delta = best_cost_time - first_meta_time
        time_to_best_ms = int(delta.total_seconds() * 1000)

    return {
        **params,
        'total_time_ms': total_time_ms,
        'time_to_best_solution_ms': time_to_best_ms,
        'effective_runs': effective_runs if effective_runs is not None else "0",
        'cost_best_solution': best_subgraph['cost'] if best_subgraph else None,
        'vertices_best_solution': best_subgraph['nvertices'] if best_subgraph else None,
        'edges_best_solution': best_subgraph['nedges'] if best_subgraph else None,
        'best_solution': f'"{best_subgraph["line"]}"' if best_subgraph else None
    }

def process_directory(directory_path):
    """Processes all log files in a directory and generates CSV output."""
    all_results = []

    # Look for files with new naming pattern (no extension or with extensions)
    patterns = [
        os.path.join(directory_path, '**', '*-*-*-*-*-*-*-*'),  # New pattern: 8 parts separated by hyphens
        os.path.join(directory_path, '**', '*-*-*-*-*-*-*-*.txt'),  # With .txt extension
        os.path.join(directory_path, '**', '*-*-*-*-*-*-*-*.txt.gz'),  # Compressed
        os.path.join(directory_path, '**', '*-*-*-*-*-*-*-*.log'),  # With .log extension
        os.path.join(directory_path, '**', '*-*-*-*-*-*-*-*.log.gz'),  # Compressed log
    ]

    all_files = set()
    for pattern in patterns:
        all_files.update(glob.glob(pattern, recursive=True))

    # Also include old pattern files for backward compatibility
    for filepath in glob.glob(os.path.join(directory_path, '**', '*.txt*'), recursive=True):
        all_files.add(filepath)

    for filepath in sorted(all_files):
        try:
            all_results.append(process_log_file(filepath))
        except Exception as e:
            print(f"Error processing {filepath}: {str(e)}")
            continue

    if not all_results:
        print("No valid log files found in directory")
        return

    # Define CSV output structure - updated to include metaheuristic
    today_date = datetime.now().strftime("%d-%m")
    output_file = f"exp_results_{today_date}.csv"
    fields = [
        ('graph_name', 'Graph'),
        ('metaheuristic', 'Metaheuristic'),
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

    # Write results to CSV (append if file exists)
    file_exists = os.path.isfile(output_file)
    with open(output_file, 'a' if file_exists else 'w') as f:
        if not file_exists:
            f.write(','.join([header for _, header in fields]) + '\n')

        for result in all_results:
            row = [str(result.get(field, '')) for field, _ in fields]
            f.write(','.join(row) + '\n')

    print(f"Results {'appended to' if file_exists else 'saved to'}: {os.path.abspath(output_file)}")
    print(f"Processed {len(all_results)} files")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: python extract_output_metrics.py <directory_path>")
        sys.exit(1)

    directory_path = sys.argv[1]
    if not os.path.isdir(directory_path):
        print(f"Error: {directory_path} is not a valid directory")
        sys.exit(1)

    process_directory(directory_path)