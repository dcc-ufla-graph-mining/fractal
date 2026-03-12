import os
import csv
import re
import sys
from pathlib import Path

class SMACParser:
    def __init__(self, input_dir, output_file):
        self.input_dir = Path(input_dir)
        self.output_file = output_file
        self.header = [
            'metaheuristic', 'obj_function', 'fractal_budget_ms', 'incumbent', 
            'threads', 'init_sol', 'timeout_ms', 'graph', 'seed', 
            'cost', 'total_time_ms', 'th_lower', 'th_upper', 'th_default', 
            'is_lower', 'is_upper', 'is_default', 'to_lower', 'to_upper', 
            'to_default', 'init_vertices'
        ]

    def get_value_from_line(self, line, key):
        """Finds 'key=value' or 'key:value' with flexibility for spaces."""
        match = re.search(rf"{key}\s*[:=]\s*([^,\s\]]+)", line)
        return match.group(1) if match else ""

    def parse_config_block(self, lines_list, start_index):
        """Extracts configuration values from the smac3 log format."""
        block_chunk = "".join(lines_list[start_index : start_index + 6])
        try:
            init_sol = re.search(r"'init_solutions':\s*(\d+)", block_chunk).group(1)
            threads = re.search(r"'threads':\s*(\d+)", block_chunk).group(1)
            timeout = re.search(r"'timeout_ms':\s*(\d+)", block_chunk).group(1)
            return (init_sol, threads, timeout)
        except AttributeError:
            return None

    def process_file(self, file_path):
        try:
            with open(file_path, 'r') as f:
                lines = f.readlines()
        except Exception as e:
            print(f"[ERROR] Could not read {file_path.name}: {e}")
            return []

        if not lines:
            return []

        # --- 1. Find the REAL first line (skip leading whitespace/empty lines) ---
        config_line = ""
        for line in lines:
            if "metaheuristic" in line:
                config_line = line.strip()
                break
        
        if not config_line:
            print(f"[WARNING] No configuration header found in {file_path.name}")
            return []

        global_data = {}
        keys_to_extract = [
            'metaheuristic', 'obj_function', 'fractal_budget_ms', 
            'th_lower', 'th_upper', 'th_default', 
            'is_lower', 'is_upper', 'is_default', 
            'to_lower', 'to_upper', 'to_default', 'init_vertices'
        ]
        
        for key in keys_to_extract:
            global_data[key] = self.get_value_from_line(config_line, key)

        # --- 2. Pass 1: Get Incumbents at the end ---
        incumbents = []
        found_exhausted = False
        for i, line in enumerate(lines):
            if "Configuration budget is exhausted" in line:
                found_exhausted = True
            if found_exhausted and "Configuration(values={" in line:
                config = self.parse_config_block(lines, i)
                if config and config not in incumbents:
                    incumbents.append(config)

        # --- 3. Pass 2: Extract data until budget exhaustion ---
        results = []
        for i, line in enumerate(lines):
            if "Configuration budget is exhausted" in line:
                break 
            
            if "Configuration(values={" in line:
                current_config = self.parse_config_block(lines, i)
                
                if current_config in incumbents:
                    inc_id = incumbents.index(current_config)
                    sub_lines = lines[i+1 : i+10]
                    graph = seed = cost = time = None
                    
                    for sl in sub_lines:
                        if "smac3_opt.py:25]" in sl: graph = sl.split(']')[-1].strip()
                        elif "smac3_opt.py:26]" in sl: seed = sl.split(']')[-1].strip()
                        elif "smac3_opt.py:47]" in sl:
                            parts = sl.split(']')[-1].strip().split()
                            if len(parts) >= 2:
                                cost, time = parts[0], parts[1]
                        elif "budget exceeded" in sl:
                            cost, time = "-1", "-1"
                    
                    if all(v is not None for v in [graph, seed, cost, time]):
                        row = global_data.copy()
                        row.update({
                            'incumbent': inc_id,
                            'init_sol': current_config[0],
                            'threads': current_config[1],
                            'timeout_ms': current_config[2],
                            'graph': graph,
                            'seed': seed,
                            'cost': cost,
                            'total_time_ms': time
                        })
                        results.append(row)
        
        return results

    def run(self):
        log_files = list(self.input_dir.rglob("*.log"))
        if not log_files:
            print(f"Error: No .log files found in {self.input_dir}")
            return

        print(f"Processing {len(log_files)} log files...")
        all_rows = []
        for log_file in log_files:
            all_rows.extend(self.process_file(log_file))

        with open(self.output_file, 'w', newline='') as csvfile:
            writer = csv.DictWriter(csvfile, fieldnames=self.header, delimiter=',')
            writer.writeheader()
            writer.writerows(all_rows)
        print(f"Done! {len(all_rows)} rows written to {self.output_file}")

if __name__ == "__main__":
    # Checks for command line argument, defaults to current directory
    input_path = sys.argv[1] if len(sys.argv) > 1 else "."
    output_name = "smac_results.csv"
    
    parser = SMACParser(input_path, output_name)
    parser.run()