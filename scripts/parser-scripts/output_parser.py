#!/usr/bin/env python3
# Extraction of experiment metrics.
# Usage: python3 output_parser.py <directory_path>

import sys
import re
import os
import gzip
import csv
from datetime import datetime, timedelta
from concurrent.futures import ProcessPoolExecutor, as_completed

# --- Pre-compile Regex Patterns (Global) ---
# Extracts repetition number from end of filename
RE_REP_NUM = re.compile(r'-(\d+)(?:\.(?:txt|log)(?:\.gz)?)?$')

# Matches "args is set to" line for parameter fallback
RE_ARGS = re.compile(r"args is set to '([^']+)'")

# Matches executor cores fallback
RE_CORES = re.compile(r"--executor-cores\s+(\d+)")

# Matches metaheuristic name fallback
RE_META_NAME = re.compile(r'(VariableNeighborhoodSearch|IteratedLocalSearch|TabuSearch)')

# Matches the "BestSubgraph" summary line at the end of logs
RE_BEST_SUBGRAPH = re.compile(r'BestSubgraph=(.*)')
RE_ELAPSED_TIME = re.compile(r'ElapsedTimeMs=(\d+)')
RE_NVERTICES = re.compile(r'nvertices=(\d+)')
RE_NEDGES = re.compile(r'nedges=(\d+)')

# Matches cost (can be "cost=X" or just a float at end of line)
RE_COST_KV = re.compile(r'cost=([\d.]+)')
RE_COST_FLOAT = re.compile(r'(\d+\.\d+)$')

# Matches metaheuristic run progress: "MetaName runNumber"
RE_META_RUN = re.compile(r'(VariableNeighborhoodSearch|IteratedLocalSearch|TabuSearch)\s+(\d+)')

def parse_timestamp(line_start):
    """
    Manually parses 'YY/MM/DD HH:MM:SS' from the start of a line.
    Example: 26/01/31 21:31:42 -> Jan 31, 2026
    """
    try:
        # Format: YY/MM/DD HH:MM:SS
        # Indices: 0123456789...

        # indices 0:2 is Year (e.g., 26 -> 2026)
        year = int(line_start[0:2]) + 2000

        # indices 3:5 is Month
        month = int(line_start[3:5])

        # indices 6:8 is Day
        day = int(line_start[6:8])

        hour = int(line_start[9:11])
        minute = int(line_start[12:14])
        second = int(line_start[15:17])

        return datetime(year, month, day, hour, minute, second)
    except (ValueError, IndexError):
        return None

def extract_parameters_from_filename(filename):
    """Extracts parameters purely from filename string."""
    basename = os.path.basename(filename)
    # Strip extensions
    basename = basename.split('.')[0]

    parts = basename.split('-')

    # Check if it matches the standard 8-part schema
    # GRAPH_NAME-META-CORES-VERT-SOLS-TIME-OBJ-RUN
    if len(parts) >= 8:
        try:
            return {
                'graph_name': parts[0],
                'metaheuristic': parts[1],
                'num_threads': parts[2],
                'initial_vertices': parts[3],
                'num_initial_solutions': parts[4],
                'timeout_ms': parts[5],
                'objective_function': parts[6],
                'repetition': parts[7]
            }
        except ValueError:
            return None
    return None

def process_single_file(filepath):
    """
    Worker function: Processes one file completely and returns a dict of results.
    """
    try:
        # 1. Init params
        params = extract_parameters_from_filename(filepath)
        if not params:
            # Init empty if filename parse failed
            params = {
                'graph_name': None, 'metaheuristic': None, 'num_threads': None,
                'initial_vertices': None, 'num_initial_solutions': None,
                'timeout_ms': None, 'objective_function': None,
                'repetition': None
            }
            # Try to get repetition from end of file if extraction failed
            match = RE_REP_NUM.search(filepath)
            if match:
                params['repetition'] = match.group(1)

        # 2. State variables for single-pass scanning
        best_subgraph_info = None
        total_time_ms = None

        first_meta_time = None
        best_cost_time = None
        current_best_cost = None

        max_run_id = -1

        # Open file (handle gzip transparently)
        open_func = gzip.open if filepath.endswith('.gz') else open

        # We need to detect if we found the "Final Summary" line or if we are just seeing intermediate costs
        final_summary_found = False

        with open_func(filepath, 'rt', encoding='utf-8', errors='ignore') as f:
            for line in f:
                # --- A. Parameter Fallback (Only if missing) ---
                if params['graph_name'] is None and "args is set to '" in line:
                    m = RE_ARGS.search(line)
                    if m:
                        args = m.group(1).split()
                        if len(args) >= 6:
                            params['graph_name'] = os.path.basename(args[0].rstrip('/'))
                            params['initial_vertices'] = args[1]
                            params['num_initial_solutions'] = args[2]
                            params['timeout_ms'] = args[4]
                            params['objective_function'] = args[5]

                if params['num_threads'] is None and "--executor-cores" in line:
                    m = RE_CORES.search(line)
                    if m: params['num_threads'] = m.group(1)

                if params['metaheuristic'] is None:
                    m = RE_META_NAME.search(line)
                    if m: params['metaheuristic'] = m.group(1)

                # --- B. Timestamp parsing ---
                # Check if line starts with a digit
                current_time = None
                if line and line[0].isdigit():
                    current_time = parse_timestamp(line)

                # --- C. Track Metaheuristic Runs ---
                # Pattern: "VariableNeighborhoodSearch 1", "IteratedLocalSearch 5", etc.
                if "Search" in line: # Quick check before Regex
                    m_run = RE_META_RUN.search(line)
                    if m_run:
                        run_id = int(m_run.group(2))
                        if run_id > max_run_id:
                            max_run_id = run_id

                        # Capture time of the VERY FIRST metaheuristic run start
                        if first_meta_time is None and current_time:
                            first_meta_time = current_time

                # --- D. Capture Best Solution ---
                # We look for the specific "BestSubgraph=" line which indicates the final result
                if "BestSubgraph=" in line:
                    final_summary_found = True
                    # Parse the Summary Line
                    m_cost = RE_COST_KV.search(line)
                    m_vert = RE_NVERTICES.search(line)
                    m_edge = RE_NEDGES.search(line)
                    m_time = RE_ELAPSED_TIME.search(line)

                    cost_val = float(m_cost.group(1)) if m_cost else 0.0

                    if m_time:
                        total_time_ms = m_time.group(1)

                    best_subgraph_info = {
                        'line': line.strip(),
                        'nvertices': m_vert.group(1) if m_vert else -1,
                        'nedges': m_edge.group(1) if m_edge else -1,
                        'cost': cost_val
                    }
                    current_best_cost = cost_val # Set this as the target to find timestamp for

        # --- E. Second Pass ---
        # If we found a best solution, we need to find WHEN it happened.
        # Since log files are append-only, the timestamp for the best cost 
        # is usually on the line where that cost was printed.
        # In the single pass above, capturing the exact time
        # of the *final* best cost is hard because we don't know it's the final one yet.
        # However, re-reading the file just for the timestamp is expensive but accurate.
        # Let's do a fast re-scan ONLY if we have a best solution but no timestamp.

        if best_subgraph_info and current_best_cost is not None:
            # Reset file pointer to find the first occurrence of this cost
            # Note: Gzip doesn't support seek(0) well, so we re-open
            with open_func(filepath, 'rt', encoding='utf-8', errors='ignore') as f:
                for line in f:
                    if str(current_best_cost) in line: # Fast string check
                        # Verify strictly
                        m_cost = RE_COST_KV.search(line) or RE_COST_FLOAT.search(line)
                        if m_cost and abs(float(m_cost.group(1)) - current_best_cost) < 1e-9:
                            ts = parse_timestamp(line)
                            if ts:
                                best_cost_time = ts
                                break # Found the first time this cost appeared

        # --- F. Calculations ---
        effective_runs = max_run_id + 1 if max_run_id > -1 else 0
        time_to_best_ms = 0
        if first_meta_time and best_cost_time:
            delta = best_cost_time - first_meta_time
            time_to_best_ms = int(delta.total_seconds() * 1000)

        # Return flat dictionary
        return {
            'graph_name': params['graph_name'],
            'metaheuristic': params['metaheuristic'],
            'initial_vertices': params['initial_vertices'],
            'num_initial_solutions': params['num_initial_solutions'],
            'timeout_ms': params['timeout_ms'],
            'objective_function': params['objective_function'],
            'num_threads': params['num_threads'],
            'repetition': params['repetition'],
            'total_time_ms': total_time_ms,
            'time_to_best_solution_ms': time_to_best_ms,
            'effective_runs': effective_runs,
            'cost_best_solution': best_subgraph_info['cost'] if best_subgraph_info else None,
            'vertices_best_solution': best_subgraph_info['nvertices'] if best_subgraph_info else None,
            'edges_best_solution': best_subgraph_info['nedges'] if best_subgraph_info else None,
            'best_solution': best_subgraph_info['line'] if best_subgraph_info else None
        }

    except Exception as e:
        print(f"Error processing {filepath}: {e}")
        return None

def main():
    if len(sys.argv) != 2:
        print("Usage: python output_parser.py <directory_path>")
        sys.exit(1)

    directory_path = sys.argv[1]
    if not os.path.isdir(directory_path):
        print(f"Error: {directory_path} is not a valid directory")
        sys.exit(1)

    # 1. Gather all files efficiently
    log_files = []
    print("Scanning directory...", end='', flush=True)
    for root, dirs, files in os.walk(directory_path):
        for file in files:
            # Simple filter: check if it looks like a log file we want
            if (file.endswith('.txt') or file.endswith('.log') or
                    file.endswith('.txt.gz') or file.endswith('.log.gz')):
                log_files.append(os.path.join(root, file))

    print(f" Found {len(log_files)} files.")

    if not log_files:
        return

    # 2. Setup CSV Output
    today_date = datetime.now().strftime("%d-%m")
    output_file = f"exp_results_{today_date}.csv"

    headers = [
        'graph_name', 'metaheuristic', 'initial_vertices', 'num_initial_solutions',
        'timeout_ms', 'objective_function', 'num_threads', 'repetition',
        'total_time_ms', 'time_to_best_solution_ms', 'effective_runs',
        'cost_best_solution', 'vertices_best_solution', 'edges_best_solution',
        'best_solution'
    ]

    # 3. Process in Parallel
    # We use CPU count - 1 to leave some room for the system
    max_workers = max(1, os.cpu_count() - 1)
    results = []

    print(f"Processing with {max_workers} threads...")

    with ProcessPoolExecutor(max_workers=max_workers) as executor:
        # Submit all jobs
        future_to_file = {executor.submit(process_single_file, f): f for f in log_files}

        # Process results as they complete
        completed_count = 0
        total_count = len(log_files)

        for future in as_completed(future_to_file):
            res = future.result()
            if res:
                results.append(res)

            completed_count += 1
            if completed_count % 100 == 0:
                print(f"Progress: {completed_count}/{total_count}")

    # 4. Write CSV
    print(f"Writing results to {output_file}...")
    file_exists = os.path.isfile(output_file)

    with open(output_file, 'a' if file_exists else 'w', newline='') as f:
        writer = csv.DictWriter(f, fieldnames=headers)
        if not file_exists:
            writer.writeheader()
        writer.writerows(results)

    print("Done.")

if __name__ == "__main__":
    main()