import sys

inputfile = sys.argv[1]

vlabels = []
adjlists = {}
elabels = {}
edgeids = {}
edges = set()

with open(inputfile, 'r') as f:
    for line in f:
        if line.startswith("#"): continue

        toks = line.split(' ')

        if line[0] == 'v':
            u = int(toks[1])
            ulabel = int(toks[2])

            assert u == len(vlabels)

            vlabels.append(ulabel)

        elif line[0] == 'e':
            u = int(toks[1])
            v = int(toks[2])
            elabel = int(toks[3])
            e = (min(u,v), max(u,v))
            elabels[e] = elabel
            edges.add(e)

            adjlist = adjlists.get(u)
            if not adjlist:
                adjlist = []
                adjlists[u] = adjlist
            adjlist.append(v)
            
            adjlist = adjlists.get(v)
            if not adjlist:
                adjlist = []
                adjlists[v] = adjlist
            adjlist.append(u)


print("%d %d\n" % (len(vlabels), len(edges)), end="")

for u in range(len(vlabels)):
    ulabel = vlabels[u]
    print("%d" % ulabel, end="")
    if u in adjlists:
        adjlist = list(set(adjlists[u]))
        adjlist.sort()

        for i in range(len(adjlist)):
            v = adjlist[i]
            if u < v:
                eid = len(edgeids)
                edgeids[(u,v)] = eid
                elabel = elabels[(u,v)]
            else:
                eid = edgeids[(v,u)]
                elabel = elabels[(v,u)]

            print(" %d,%d,%d" % (v,eid,elabel), end="")
            #print(" %d,%d" % (v,eid), end="")

    print("\n", end="")

