import networkx as nx
import matplotlib.pyplot as plt

# 1. Create/Load a Graph: Karate Club Graph
G = nx.karate_club_graph()

# 2. Identify a Dense Subgraph of exactly 5 vertices
# Nodes 0, 1, 2, 3, and 7 are often part of the "Mr. Hi" community and are well-connected.
dense_subgraph_nodes = [0, 1, 2, 3, 7] # A dense group of 5 nodes

print(f"Nodes in the identified dense subgraph: {dense_subgraph_nodes}")

# 3. Draw the Graph

plt.figure(figsize=(10, 8))

# Define colors and sizes for nodes
node_colors = []
node_size = 500 # Unified size for all nodes
highlight_color = 'red'
default_color = 'skyblue'

for node in G.nodes():
    if node in dense_subgraph_nodes:
        node_colors.append(highlight_color)
    else:
        node_colors.append(default_color)

# Define edge colors for highlighting
edge_colors = []
edge_widths = []
highlight_edge_color = 'darkred'
default_edge_color = 'gray'
highlight_edge_width = 3
default_edge_width = 1

for u, v in G.edges():
    # Check if both nodes of the edge are in the dense subgraph
    if u in dense_subgraph_nodes and v in dense_subgraph_nodes:
        edge_colors.append(highlight_edge_color)
        edge_widths.append(highlight_edge_width)
    else:
        edge_colors.append(default_edge_color)
        edge_widths.append(default_edge_width)

# Use a spring layout for better visualization of graph structure
# Adjust 'k' for optimal distance to reduce overlap if necessary.
pos = nx.spring_layout(G, seed=42, k=0.3) # Seed for reproducibility, k can be tuned

# Draw nodes
nx.draw_networkx_nodes(G, pos, node_color=node_colors, node_size=node_size, alpha=0.9)

# Draw edges
nx.draw_networkx_edges(G, pos, edge_color=edge_colors, width=edge_widths, alpha=0.7)

# No labels are drawn on nodes.
# No title is set for the plot.

plt.axis('off') # Turn off the axis

# Apply tight layout to ensure everything fits without excessive borders
plt.tight_layout()

# Save the figure as a PDF
plt.savefig("karate_club_dense_subgraph.png", format="png")

print("Figure saved as 'karate_club_dense_subgraph.png'")
