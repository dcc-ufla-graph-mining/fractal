import sys

logfile = sys.argv[1]

# first pass: compute subgraphs and subgraph IDs
last_subgraph_per_run = dict()
n_steps_per_run = dict()    
subgraph_to_cost_nvertices_nedges = dict()
with open(logfile, 'r') as f:
    for line in f:
        if "VNSSubgraphOptimization" not in line: continue
        toks = line.split(" ")
        run = int(toks[3])
        cost = float(toks[7])
        nvertices = int(toks[4])
        nedges = int(toks[5])
        subgraph_str = toks[6]
        t = subgraph_str[0]
        #print(toks)

        if t == '/': # shake
            subgraph_str = subgraph_str[1:]
            t = subgraph_str[0]

        if t == '+': # add vertex
            subgraph_str = subgraph_str[1:]
            subgraph = last_subgraph_per_run[run].copy()
            subgraph.add(int(subgraph_str))
            subgraph_to_cost_nvertices_nedges[frozenset(subgraph)] = (cost, nvertices, nedges)
            last_subgraph_per_run[run] = subgraph
        elif t == '-': # remove vertex
            subgraph_str = subgraph_str[1:]
            subgraph = last_subgraph_per_run[run].copy()
            subgraph.remove(int(subgraph_str))
            subgraph_to_cost_nvertices_nedges[frozenset(subgraph)] = (cost, nvertices, nedges)
            last_subgraph_per_run[run] = subgraph
        else: # first or last
            subgraph = set()
            vertices = subgraph_str.split(",")
            for u in vertices:
                u = int(u)
                subgraph.add(u)
            subgraph_to_cost_nvertices_nedges[frozenset(subgraph)] = (cost, nvertices, nedges)
            last_subgraph_per_run[run] = subgraph

del last_subgraph_per_run

# second pass: write mapping for UNIQUE subgraphs
subgraph_keys = list(subgraph_to_cost_nvertices_nedges.keys())
subgraph_keys.sort()
subgraph_to_id = dict()

with open("%s.smap" % logfile, 'w') as f:
    f.write("subgraph_id num_vertices num_edges cost subgraph\n")
    for subgraph in subgraph_keys:
        s_id = len(subgraph_to_id)
        subgraph_to_id[subgraph] = s_id
        cost, nvertices, nedges = subgraph_to_cost_nvertices_nedges[subgraph]
        s = list(subgraph)
        s.sort()
        s = [str(u) for u in s]
        f.write("%d %d %d %f " % (s_id, nvertices, nedges, cost))
        f.write(",".join(s))
        f.write("\n")

print("MapFileWritten %s.smap columns=subgraph_id,num_vertices,num_edges,cost,subgraph" % logfile)

del subgraph_keys
del subgraph_to_cost_nvertices_nedges

# third pass: write STN file based on subgraph IDs
last_subgraph_per_run = dict()
with open(logfile, 'r') as f, open("%s.stn" % logfile, 'w') as stnf:
    for line in f:
        if "VNSSubgraphOptimization" not in line: continue
        toks = line.split(" ")
        run = int(toks[3])
        cost = float(toks[7])
        subgraph_str = toks[6]
        t = subgraph_str[0]
        #print(toks)

        if t == '/': # shake
            subgraph_str = subgraph_str[1:]
            t = subgraph_str[0]

        if t == '+': # add vertex
            subgraph_str = subgraph_str[1:]
            subgraph = last_subgraph_per_run[run].copy()
            subgraph.add(int(subgraph_str))
            stnf.write("%d %f %d\n" % (run, cost, subgraph_to_id[frozenset(subgraph)]))
            last_subgraph_per_run[run] = subgraph
        elif t == '-': # remove vertex
            subgraph_str = subgraph_str[1:]
            subgraph = last_subgraph_per_run[run].copy()
            subgraph.remove(int(subgraph_str))
            stnf.write("%d %f %d\n" % (run, cost, subgraph_to_id[frozenset(subgraph)]))
            last_subgraph_per_run[run] = subgraph
        else: # first or last
            subgraph = set()
            vertices = subgraph_str.split(",")
            for u in vertices:
                u = int(u)
                subgraph.add(u)
            stnf.write("%d %f %d\n" % (run, cost, subgraph_to_id[frozenset(subgraph)]))
            last_subgraph_per_run[run] = subgraph

print("STNFileWritten %s.stn columns=stn_run,cost,subgraph_id" % logfile)
