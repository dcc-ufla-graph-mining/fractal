import os
import networkx as nx
import time
import numpy as np
import math

def load_custom_graph(graph_dir):
    """
    Loads a graph from a custom directory format into a NetworkX Graph.

    Parameters:
    - graph_dir (str): Path to the graph directory containing 'metadata' and 'adjlists' files.

    Returns:
    - G (networkx.Graph): The loaded graph.
    """
    metadata_path = os.path.join(graph_dir, 'metadata')
    adjlists_path = os.path.join(graph_dir, 'adjlists')

    # Read metadata
    with open(metadata_path, 'r') as f:
        n, m = map(int, f.readline().strip().split())

    G = nx.Graph()
    G.add_nodes_from(range(n))  # Ensure nodes 0 to n-1 are present

    # Read adjlists
    with open(adjlists_path, 'r') as f:
        for u, line in enumerate(f):
            entries = line.strip().split()
            for entry in entries:
                if not entry:
                    continue
                v_str, _ = entry.split(',')  # Ignore edge ID
                v = int(v_str)
                if not G.has_edge(u, v):  # Avoid adding duplicate edges
                    G.add_edge(u, v)

    return G

if __name__ == '__main__':
    import sys
    if len(sys.argv) != 2:
        print(f"Usage: python {sys.argv[0]} <graph_directory>")
        sys.exit(1)

    start = time.time()
    graph_dir = sys.argv[1]
    G = load_custom_graph(graph_dir)
    elapsed = time.time() - start
    print(f"Graph with {G.number_of_nodes()} nodes and {G.number_of_edges()} edges")
    print(f"ReadGraphElapsedSeconds {elapsed}")

    start = time.time()
    node_degrees = np.zeros(G.number_of_nodes(), dtype=int)
    max_node_degree = 0
    for u in G.nodes():
        degree = nx.degree(G, u)
        node_degrees[u] = degree
        if degree > max_node_degree:
            max_node_degree = degree

    elapsed = time.time() - start

    print(f"GetNodeDegreesElapsedSeconds {elapsed}")

    start = time.time()
    num_nodes_per_degree = {}
    for u in G.nodes():
        degree = node_degrees[u]
        num_nodes_per_degree[degree] = num_nodes_per_degree.get(degree, 0) + 1
    current_entropy = 0
    for count in num_nodes_per_degree:
        if count == 0: continue
        p = count / G.number_of_nodes()
        current_entropy = current_entropy - (p * math.log2(p))
    elapsed = time.time() - start
    print(f"InitialDegreeEntropy {current_entropy}")
    print(f"GetNumNodesPerDegreeElapsedSeconds {elapsed}")

    # priority is lower bound on degree count produced from removal (larger priority first)
    def node_priority(G, u, node_degrees, num_nodes_per_degree):
        u_degree = node_degrees[u]
        counts_by_degree = {}
        counts_by_degree[u_degree] = num_nodes_per_degree[u_degree] - 1

        for v in G[u]:
            d_before = node_degrees[v]
            counts_by_degree[d_before] = num_nodes_per_degree[d_before]

        for v in G[u]:
            d_before = node_degrees[v]
            d_after = d_before - 1
            counts_by_degree[d_before] = counts_by_degree.get(d_before, 0) - 1
            counts_by_degree[d_after] = counts_by_degree.get(d_after, 0) + 1

        delta = 0

        prio = min(counts_by_degree.values())

        return prio


    start = time.time()
    buckets = [None] * (G.number_of_nodes() + 1)
    for c in range(len(buckets)):
        buckets[c] = set()
    for u in G.nodes():
        prio = node_priority(G, u, node_degrees, num_nodes_per_degree)
        buckets[prio].add(u)
    elapsed = time.time() - start
    print(f"BuildBucketsElapsedSeconds {elapsed}")

    start = time.time()
    min_prio = 0
    max_prio = G.number_of_nodes()
    num_initial_nodes = G.number_of_nodes()
    best_score = 0
    best_num_nodes = num_initial_nodes
    best_num_edges = G.number_of_edges()

    while G.number_of_nodes() > 0:
        while max_prio >= min_prio:
            b = buckets[max_prio]
            if b is not None and len(b) > 0: break
            max_prio -= 1

        selected_node = b.pop()
        d = node_degrees[selected_node]
        num_nodes_per_degree[d] = num_nodes_per_degree[d] - 1
        node_degrees[selected_node] = 0

        neighbors = list(G[selected_node])
        for v in G[selected_node]: # update
            d_before = node_degrees[v]
            d_after = d_before - 1
            num_nodes_per_degree[d_before] = num_nodes_per_degree[d_before] - 1
            num_nodes_per_degree[d_after] = num_nodes_per_degree.get(d_after, 0) + 1
            node_degrees[v] = d_after

        G.remove_node(selected_node)
        score = 0
        if score > best_score:
            best_score = score
            best_num_nodes = G.number_of_nodes()
            best_num_edges = G.number_of_edges()

        if G.number_of_nodes() % 1000 == 0:
            print("CurrentNumNodes:", G.number_of_nodes(),
                  "CurrentPriority:", max_prio,
                  "CurrentScore:", score,
                  "BestScore:", best_score,
                  "BestNumNodes:", best_num_nodes,
                  "BestNumEdges:", best_num_edges)

    elapsed = time.time() - start
    print(f"PeelingResultBestScore {best_score}")
    print(f"PeelingResultNumNodes {best_num_nodes}")
    print(f"PeelingResultNumEdges {best_num_edges}")
    print(f"PeelingElapsedSeconds {elapsed}")
