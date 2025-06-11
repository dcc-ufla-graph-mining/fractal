import os
from collections import Counter

import networkx as nx
import time
import numpy as np
from networkx.classes import neighbors


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
                if u < v:  # Avoid adding duplicate edges
                    G.add_edge(u, v)

            if u % 10000 == 0:
                print(f'ReadGraph {u}/{n}', flush=True)

    return G

if __name__ == '__main__':
    import sys
    if len(sys.argv) != 3:
        print(f"Usage: python {sys.argv[0]} <graph_directory> <epsilon>")
        sys.exit(1)

    start = time.time()
    graph_dir = sys.argv[1]
    epsilon = float(sys.argv[2])
    G = load_custom_graph(graph_dir)
    elapsed = time.time() - start
    print(f"Graph with {G.number_of_nodes()} nodes and {G.number_of_edges()} edges")
    print(f"ReadGraphElapsedSeconds {elapsed}")

    start = time.time()
    triangle_counts = np.zeros(G.number_of_nodes(), dtype=int)
    max_num_triangles = 0
    total_num_triangles = 0
    n = G.number_of_nodes()

    triangle_counts = Counter(dict.fromkeys(G, 0))
    for node1, node1_nbrs in G.adjacency():
        node1_nbrs = node1_nbrs.keys()
        for node2 in node1_nbrs:
            if node1 >= node2: continue
            node2_nbrs = {u for u in G[node2] if u > node2}
            third_nodes = node1_nbrs & node2_nbrs
            m = len(third_nodes)
            triangle_counts[node1] += m
            triangle_counts[node2] += m
            triangle_counts.update(third_nodes)
        if node1 % 10000 == 0:
            print(f'ComputeTriangles {node1}/{n}', flush=True)

    for u in G.nodes():
        num_triangles = triangle_counts[u]
        total_num_triangles += num_triangles
        if num_triangles > max_num_triangles:
            max_num_triangles = num_triangles


    elapsed = time.time() - start
    print(f"CountTrianglesElapsedSeconds {elapsed}", flush=True)

    print("MaxNumTriangles", max_num_triangles, flush=True)

    start = time.time()
    buckets = [None] * (max_num_triangles + 1)
    for c in range(len(buckets)):
        buckets[c] = set()
    for u in G.nodes():
        buckets[triangle_counts[u]].add(u)
    elapsed = time.time() - start
    print(f"BuildBucketsElapsedSeconds {elapsed}", flush=True)

    start = time.time()
    min_prio = 0
    num_initial_nodes = G.number_of_nodes()
    score = (total_num_triangles / 3) / num_initial_nodes
    best_score = score
    best_num_nodes = num_initial_nodes
    best_num_edges = G.number_of_edges()

    while G.number_of_nodes() > 0:
        selected_nodes = set()
        while min_prio <= 3 * (1 + epsilon) * score and G.number_of_nodes() - len(selected_nodes) > 0:
            while min_prio <= max_num_triangles:
                b = buckets[min_prio]
                if b is not None and len(b) > 0: break
                min_prio += 1

            if min_prio <= 3 * (1 + epsilon) * score:
                #print('add_node', min_prio, 3 * (1 + epsilon) * score)
                selected_node = b.pop()
                selected_nodes.add(selected_node)
            else:
                break

        for selected_node in selected_nodes:
            neighbors = list(G[selected_node])
            to_remove_triangles = {}
            for i in range(len(neighbors)):
                v = neighbors[i]
                for j in range(i + 1, len(neighbors)):
                    w = neighbors[j]
                    if G.has_edge(v, w): # one less triangle for v and w
                        if v not in selected_nodes:
                            to_remove_triangles[v] = to_remove_triangles.get(v, 0) + 1
                        if w not in selected_nodes:
                            to_remove_triangles[w] = to_remove_triangles.get(w, 0) + 1

                        total_num_triangles -= 3

            for v in to_remove_triangles:
                num_removed_triangles = to_remove_triangles[v]
                old_num_triangles = triangle_counts[v]
                new_num_triangles = old_num_triangles - num_removed_triangles
                buckets[old_num_triangles].remove(v)
                buckets[new_num_triangles].add(v)
                triangle_counts[v] = new_num_triangles
                if new_num_triangles < min_prio:
                    min_prio = new_num_triangles

            G.remove_node(selected_node)

        score = (total_num_triangles / 3) / G.number_of_nodes() \
            if G.number_of_nodes() > 0 else 0

        if score > best_score:
            best_score = score
            best_num_nodes = G.number_of_nodes()
            best_num_edges = G.number_of_edges()

        print("IterationPeelingSize", len(selected_nodes),
              "CurrentNumNodes:", G.number_of_nodes(),
              "CurrentPriority:", min_prio,
              "CurrentScore:", score,
              "BestScore:", best_score,
              "BestNumNodes:", best_num_nodes,
              "BestNumEdges:", best_num_edges, flush=True)

    elapsed = time.time() - start
    print(f"PeelingResultBestScore {best_score}")
    print(f"PeelingResultNumNodes {best_num_nodes}")
    print(f"PeelingResultNumEdges {best_num_edges}")
    print(f"PeelingElapsedSeconds {elapsed}")
