import sys
import os

def process_file(file_name):
    info = {}
    with open(file_name, 'r') as f:
        for line in f.readlines():
            line = line.strip()
            if len(line) == 0: continue

            toks = [t for t in line.split(" ") if len(t) > 0]
            if len(toks) == 0: continue


            #        120,455.24 msec task-clock                       #   17.191 CPUs utilized
            if 'task-clock' in line:
                counter = float(toks[0].replace(',', ''))
                counter_unit = toks[1]
                cpus_utilized = float(toks[4])
                info['task_clock_counter'] = counter
                info['task_clock_unit'] = counter_unit
                info['task_clock_cpus_utilized'] = cpus_utilized

            #           625,025      context-switches                 #    5.189 K/sec
            elif 'context-switches' in line:
                counter = int(toks[0].replace(',', ''))
                counter_perc = float(toks[3])
                counter_over_time_unit = toks[4]
                info['context_switches_counter'] = counter
                info['context_switches_over_time'] = counter_perc
                info['context_switches_over_time_unit'] = counter_over_time_unit

            #           134,107      cpu-migrations                   #    1.113 K/sec
            elif 'cpu-migrations' in line:
                counter = int(toks[0].replace(',', ''))
                counter_perc = float(toks[3])
                counter_over_time_unit = toks[4]
                info['cpu_migrations_counter'] = counter
                info['cpu_migrations_over_time'] = counter_perc
                info['cpu_migrations_over_time_unit'] = counter_over_time_unit

            #            59,513      page-faults                      #  494.067 /sec
            elif 'page-faults' in line:
                counter = int(toks[0].replace(',', ''))
                counter_perc = float(toks[3])
                counter_over_time_unit = toks[4]
                info['page_faults_counter'] = counter
                info['page_faults_over_time'] = counter_perc
                info['page_faults_over_time_unit'] = counter_over_time_unit

            #   603,930,191,077      cpu_core/cycles/                 #    5.014 G/sec                    (43.92%)
            elif 'cpu_core/cycles/' in line:
                counter = int(toks[0].replace(',', ''))
                counter_perc = float(toks[3])
                counter_over_time_unit = toks[4]
                coverage = float(toks[5].replace('(', '').replace(')', '').replace('%', ''))
                info['cpu_core_cycles_counter'] = counter
                info['cpu_core_cycles_over_time'] = counter_perc
                info['cpu_core_cycles_over_time_unit'] = counter_over_time_unit
                info['cpu_core_coverage'] = coverage

            #   490,906,313,391      cpu_atom/cycles/                 #    4.075 G/sec                    (57.81%)
            elif 'cpu_atom/cycles/' in line:
                counter = int(toks[0].replace(',', ''))
                counter_perc = float(toks[3])
                counter_over_time_unit = toks[4]
                coverage = float(toks[5].replace('(', '').replace(')', '').replace('%', ''))
                info['cpu_atom_cycles_counter'] = counter
                info['cpu_atom_cycles_over_time'] = counter_perc
                info['cpu_atom_cycles_over_time_unit'] = counter_over_time_unit
                info['cpu_atom_coverage'] = coverage

            # 1,605,196,742,948      cpu_core/instructions/           #   13.326 G/sec                    (43.92%)
            elif 'cpu_core/instructions/' in line:
                counter = int(toks[0].replace(',', ''))
                counter_perc = float(toks[3])
                counter_over_time_unit = toks[4]
                coverage = float(toks[5].replace('(', '').replace(')', '').replace('%', ''))
                info['cpu_core_instructions_counter'] = counter
                info['cpu_core_instructions_over_time'] = counter_perc
                info['cpu_core_instructions_over_time_unit'] = counter_over_time_unit
                info['cpu_core_instructions_coverage'] = coverage

            # 1,069,199,470,215      cpu_atom/instructions/           #    8.876 G/sec                    (57.81%)
            elif 'cpu_atom/instructions/' in line:
                counter = int(toks[0].replace(',', ''))
                counter_perc = float(toks[3])
                counter_over_time_unit = toks[4]
                coverage = float(toks[5].replace('(', '').replace(')', '').replace('%', ''))
                info['cpu_atom_instructions_counter'] = counter
                info['cpu_atom_instructions_over_time'] = counter_perc
                info['cpu_atom_instructions_over_time_unit'] = counter_over_time_unit
                info['cpu_atom_instructions_coverage'] = coverage

            #   361,142,544,293      cpu_core/branches/               #    2.998 G/sec                    (43.92%)
            elif 'cpu_core/branches/' in line:
                counter = int(toks[0].replace(',', ''))
                counter_perc = float(toks[3])
                counter_over_time_unit = toks[4]
                coverage = float(toks[5].replace('(', '').replace(')', '').replace('%', ''))
                info['cpu_core_branches_counter'] = counter
                info['cpu_core_branches_over_time'] = counter_perc
                info['cpu_core_branches_over_time_unit'] = counter_over_time_unit
                info['cpu_core_branches_coverage'] = coverage

            #   239,199,050,003      cpu_atom/branches/               #    1.986 G/sec                    (57.81%)
            elif 'cpu_atom/branches/' in line:
                counter = int(toks[0].replace(',', ''))
                counter_perc = float(toks[3])
                counter_over_time_unit = toks[4]
                coverage = float(toks[5].replace('(', '').replace(')', '').replace('%', ''))
                info['cpu_atom_branches_counter'] = counter
                info['cpu_atom_branches_over_time'] = counter_perc
                info['cpu_atom_branches_over_time_unit'] = counter_over_time_unit
                info['cpu_atom_branches_coverage'] = coverage

            #     5,964,104,723      cpu_core/branch-misses/          #   49.513 M/sec                    (43.92%)
            elif 'cpu_core/branch-misses/' in line:
                counter = int(toks[0].replace(',', ''))
                counter_perc = float(toks[3])
                counter_over_time_unit = toks[4]
                coverage = float(toks[5].replace('(', '').replace(')', '').replace('%', ''))
                info['cpu_core_branch_misses_counter'] = counter
                info['cpu_core_branch_misses_over_time'] = counter_perc
                info['cpu_core_branch_misses_over_time_unit'] = counter_over_time_unit
                info['cpu_core_branch_misses_coverage'] = coverage

            #     4,780,967,256      cpu_atom/branch-misses/          #   39.691 M/sec                    (57.81%)
            elif 'cpu_atom/branch-misses/' in line:
                counter = int(toks[0].replace(',', ''))
                counter_perc = float(toks[3])
                counter_over_time_unit = toks[4]
                coverage = float(toks[5].replace('(', '').replace(')', '').replace('%', ''))
                info['cpu_atom_branch_misses_counter'] = counter
                info['cpu_atom_branch_misses_over_time'] = counter_perc
                info['cpu_atom_branch_misses_over_time_unit'] = counter_over_time_unit
                info['cpu_atom_branch_misses_coverage'] = coverage

            # 3,097,097,371,341      cpu_core/slots/                  #   25.712 G/sec                    (43.92%)
            elif 'cpu_core/slots/' in line:
                counter = int(toks[0].replace(',', ''))
                counter_perc = float(toks[3])
                counter_over_time_unit = toks[4]
                coverage = float(toks[5].replace('(', '').replace(')', '').replace('%', ''))
                info['cpu_core_slots_counter'] = counter
                info['cpu_core_slots_over_time'] = counter_perc
                info['cpu_core_slots_over_time_unit'] = counter_over_time_unit
                info['cpu_core_slots_coverage'] = coverage

            # 1,077,365,849,393      cpu_core/topdown-retiring/       #     36.5% Retiring                (43.92%)
            elif 'cpu_core/topdown-retiring/' in line:
                counter = int(toks[0].replace(',', ''))
                counter_perc = float(toks[3].replace('%', ''))
                coverage = float(toks[5].replace('(', '').replace(')', '').replace('%', ''))
                info['cpu_core_topdown_retiring_counter'] = counter
                info['cpu_core_topdown_retiring_perc'] = counter_perc
                info['cpu_core_topdown_retiring_coverage'] = coverage

            #   538,856,094,274      cpu_core/topdown-bad-spec/       #     18.3% Bad Speculation         (43.92%)
            elif 'cpu_core/topdown-bad-spec/' in line:
                counter = int(toks[0].replace(',', ''))
                counter_perc = float(toks[3].replace('%', ''))
                coverage = float(toks[6].replace('(', '').replace(')', '').replace('%', ''))
                info['cpu_core_topdown_bad_spec_counter'] = counter
                info['cpu_core_topdown_bad_spec_perc'] = counter_perc
                info['cpu_core_topdown_bad_spec_coverage'] = coverage

            #   637,595,544,317      cpu_core/topdown-fe-bound/       #     21.6% Frontend Bound          (43.92%)
            elif 'cpu_core/topdown-fe-bound/' in line:
                counter = int(toks[0].replace(',', ''))
                counter_perc = float(toks[3].replace('%', ''))
                coverage = float(toks[6].replace('(', '').replace(')', '').replace('%', ''))
                info['cpu_core_topdown_fe_bound_counter'] = counter
                info['cpu_core_topdown_fe_bound_perc'] = counter_perc
                info['cpu_core_topdown_fe_bound_coverage'] = coverage

            #   696,272,472,772      cpu_core/topdown-be-bound/       #     23.6% Backend Bound           (43.92%)
            elif 'cpu_core/topdown-be-bound/' in line:
                counter = int(toks[0].replace(',', ''))
                counter_perc = float(toks[3].replace('%', ''))
                coverage = float(toks[6].replace('(', '').replace(')', '').replace('%', ''))
                info['cpu_core_topdown_be_bound_counter'] = counter
                info['cpu_core_topdown_be_bound_perc'] = counter_perc
                info['cpu_core_topdown_be_bound_coverage'] = coverage

            #    22,540,403,626      cpu_core/topdown-heavy-ops/      #      0.8% Heavy Operations       #     35.8% Light Operations        (43.92%)
            elif 'cpu_core/topdown-heavy-ops/' in line:
                counter = int(toks[0].replace(',', ''))
                counter_perc_heavy = float(toks[3].replace('%', ''))
                counter_perc_light = float(toks[7].replace('%', ''))
                coverage = float(toks[10].replace('(', '').replace(')', '').replace('%', ''))
                info['cpu_core_topdown_heavy_ops_counter'] = counter
                info['cpu_core_topdown_heavy_ops_heavy_perc'] = counter_perc_heavy
                info['cpu_core_topdown_heavy_ops_light_perc'] = counter_perc_light
                info['cpu_core_topdown_heavy_ops_coverage'] = coverage

            #   510,238,966,324      cpu_core/topdown-br-mispredict/  #     17.3% Branch Mispredict      #      1.0% Machine Clears          (43.92%)
            elif 'cpu_core/topdown-br-mispredict/' in line:
                counter = int(toks[0].replace(',', ''))
                counter_perc = float(toks[3].replace('%', ''))
                counter_perc_clears = float(toks[7].replace('%', ''))
                coverage = float(toks[10].replace('(', '').replace(')', '').replace('%', ''))
                info['cpu_core_topdown_br_mispredict_counter'] = counter
                info['cpu_core_topdown_br_mispredict_perc'] = counter_perc
                info['cpu_core_topdown_br_mispredict_machine_clears_perc'] = counter_perc_clears
                info['cpu_core_topdown_br_mispredict_coverage'] = coverage

            #   289,710,277,243      cpu_core/topdown-fetch-lat/      #      9.8% Fetch Latency          #     11.8% Fetch Bandwidth         (43.92%)
            elif 'cpu_core/topdown-fetch-lat/' in line:
                counter = int(toks[0].replace(',', ''))
                counter_perc = float(toks[3].replace('%', ''))
                counter_perc_bandwidth = float(toks[7].replace('%', ''))
                coverage = float(toks[10].replace('(', '').replace(')', '').replace('%', ''))
                info['cpu_core_topdown_fetch_lat_counter'] = counter
                info['cpu_core_topdown_fetch_lat_perc'] = counter_perc
                info['cpu_core_topdown_fetch_lat_bandwidth_perc'] = counter_perc_bandwidth
                info['cpu_core_topdown_fetch_lat_coverage'] = coverage

            #   482,708,557,965      cpu_core/topdown-mem-bound/      #     16.4% Memory Bound           #      7.2% Core Bound              (43.92%)
            elif 'cpu_core/topdown-mem-bound/' in line:
                counter = int(toks[0].replace(',', ''))
                counter_perc = float(toks[3].replace('%', ''))
                counter_perc_core_bound = float(toks[7].replace('%', ''))
                coverage = float(toks[10].replace('(', '').replace(')', '').replace('%', ''))
                info['cpu_core_topdown_mem_bound_counter'] = counter
                info['cpu_core_topdown_mem_bound_perc'] = counter_perc
                info['cpu_core_topdown_mem_bound_core_bound_perc'] = counter_perc_core_bound
                info['cpu_core_topdown_mem_bound_coverage'] = coverage

            #       7.006951268 seconds time elapsed
            elif 'time elapsed' in line:
                time_elapsed = float(toks[0])
                time_elapsed_unit = toks[1]
                info['time_elapsed_unit'] = time_elapsed_unit

    return info

# ils-citeseer-32-10-100-3000-triangledensestsubgraph-4_tma_3227789.txt
def get_info_from_filename(file_name):
    file_name = os.path.basename(file_name)
    toks = file_name.split('-')
    graph = toks[0]
    metaheuristic = toks[1]
    nthreads = int(toks[2])
    size_initial_sol = int(toks[3])
    num_initial_sol = int(toks[4])
    timeout_ms = int(toks[5])
    obj_function = toks[6]
    toks = toks[7].split("_")
    repetition = int(toks[0])

    return {
        'graph': graph,
        'metaheuristic': metaheuristic,
        'size_initial_sol': size_initial_sol,
        'num_initial_sol': num_initial_sol,
        'timeout_ms': timeout_ms,
        'obj_function': obj_function,
        'num_threads': nthreads,
        'repetition': repetition
    }


columns = None
for file_name in sys.argv[1:]:
    info_from_filename = get_info_from_filename(file_name)
    info = process_file(file_name)
    info = info_from_filename | info
    if columns is None:
        columns = list(info.keys())
        print(','.join(columns))

    print(','.join([str(info[c]) for c in columns]))
