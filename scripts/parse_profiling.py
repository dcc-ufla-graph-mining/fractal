import sys
import os
import gzip

def process_file(file_name):
    info = {}
    with gzip.open(file_name, 'rt') as f:
        line = next(f)
        assert 'Execution profile' in line
        # read header
        # --- Execution profile ---
        # Total samples       : 73525
        # unknown_Java        : 492 (0.67%)
        # not_walkable_Java   : 18 (0.02%)
        # deoptimization      : 1 (0.00%)

        line = next(f).strip()
        while len(line) > 0:
            if 'Total samples' in line:
                total_samples = int(line.split(":")[1])
                info['total_samples'] = total_samples
            line = next(f).strip()

        num_samples_obj_function_call = 0
        num_samples_obj_function_call_perc = 0
        time_obj_function_call_ns = 0

        line = next(f).strip()
        while line.startswith('---'):
            toks = line.split(" ")
            time_ns = int(toks[1])
            time_perc = float(toks[3].replace('(', '').replace(')', ''). replace('%', '').replace(',', ''))
            num_samples = int(toks[4])

            is_obj_function_call = False

            line = next(f).strip()
            while len(line) > 0 and line[0] == '[':
                if 'VertexInducedOptimizationSubgraph.recalculateCost' in line:
                    is_obj_function_call = True
                line = next(f).strip()

            if is_obj_function_call:
                num_samples_obj_function_call += num_samples
                num_samples_obj_function_call_perc += time_perc
                time_obj_function_call_ns += time_ns

            line = next(f).strip()

        info['obj_function_sampĺes'] = num_samples_obj_function_call
        info['obj_function_perc_by_sum'] = num_samples_obj_function_call_perc
        info['obj_function_time_ns'] = time_obj_function_call_ns

        info['obj_function_perc'] = (num_samples_obj_function_call / total_samples) * 100

    return info

# citeseer-32-10-1000-2000-triangledensestsubgraph_1-profiling.txt.gz
def get_info_fro_filename(file_name):
    file_name = os.path.basename(file_name)
    toks = file_name.split('-')
    graph = toks[0]
    nthreads = int(toks[1])
    size_initial_sol = int(toks[2])
    num_initial_sol = int(toks[3])
    timeout_vns = int(toks[4])
    toks = toks[5].split("_")
    obj_function = toks[0]
    repetition = int(toks[1].replace('.txt.gz', '').split("-")[0])

    return {
        'graph': graph,
        'num_threads': nthreads,
        'size_initial_sol': size_initial_sol,
        'num_initial_sol': num_initial_sol,
        'timeout_vns': timeout_vns,
        'obj_function': obj_function,
        'repetition': repetition
    }


columns = None
for file_name in sys.argv[1:]:
    info_from_filename = get_info_fro_filename(file_name)
    info = process_file(file_name)
    info = info_from_filename | info
    if columns is None:
        columns = list(info.keys())
        print(','.join(columns))

    print(','.join([str(info[c]) for c in columns]))
