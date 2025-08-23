package br.ufmg.cs.systems.fractal.optimization;

import com.koloboke.collect.set.IntSet;
import com.koloboke.collect.set.hash.HashIntSets;
import com.koloboke.collect.map.hash.HashIntObjMaps;
import com.koloboke.collect.map.IntObjMap;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;

public class GreedyPeeling {

    // --- Custom IntIntPredicate ---
    @FunctionalInterface
    public interface IntIntPredicate {
        boolean test(int a, int b);
    }

    // --- PrimitiveGraphView ---
    public static class PrimitiveGraphView {
        private final IntPredicate vertexPredicate;
        private final IntIntPredicate edgePredicate;
        private final IntSet vertexUniverse;
        private final IntObjMap<IntSet> adj;

        private final int vertexCount;
        private final int edgeCount;

        public PrimitiveGraphView(
                IntSet vertexUniverse,
                IntObjMap<IntSet> adj,
                IntPredicate vertexPredicate,
                IntIntPredicate edgePredicate
        ) {
            this.vertexUniverse = vertexUniverse;
            this.adj = adj;
            this.vertexPredicate = vertexPredicate;
            this.edgePredicate = edgePredicate;

            int vc = 0, ec = 0;
            for (int u : vertexUniverse) {
                if (!vertexPredicate.test(u)) continue;
                vc++;
                ec += (int) neighbors(u).filter(v -> u < v).count();
            }
            this.vertexCount = vc;
            this.edgeCount = ec;
        }

        public boolean containsVertex(int v) {
            return vertexPredicate.test(v);
        }

        public boolean containsEdge(int u, int v) {
            return edgePredicate.test(u, v);
        }

        public IntSet getVertexUniverse() {
            return vertexUniverse;
        }

        public int getVertexCount() {
            return vertexCount;
        }

        public int getEdgeCount() {
            return edgeCount;
        }

        public Iterable<Integer> filteredVertices() {
            return () -> vertexUniverse.stream().mapToInt(Integer::intValue).filter(vertexPredicate).iterator();
        }

        public IntStream neighbors(int u) {
            IntSet neighbors = adj.getOrDefault(u, HashIntSets.newMutableSet());
            return neighbors.stream().mapToInt(Integer::intValue).filter(v -> vertexPredicate.test(v) && edgePredicate.test(u, v));
        }
    }

    // --- Graph Loader ---
    public static class Graph {
        public final IntObjMap<IntSet> adj;
        public final IntSet vertices;

        public Graph(IntObjMap<IntSet> adj, IntSet vertices) {
            this.adj = adj;
            this.vertices = vertices;
        }

        public static Graph loadFromDirectory(String dirPath) throws IOException {
            Path metadataPath = Paths.get(dirPath, "metadata");
            Path adjlistPath = Paths.get(dirPath, "adjlists");

            BufferedReader metaReader = Files.newBufferedReader(metadataPath);
            String[] meta = metaReader.readLine().split(" ");
            int n = Integer.parseInt(meta[0]);

            IntObjMap<IntSet> adj = HashIntObjMaps.newMutableMap();
            IntSet vertices = HashIntSets.newMutableSet();

            BufferedReader adjReader = Files.newBufferedReader(adjlistPath);
            String line;
            int u = 0;
            while ((line = adjReader.readLine()) != null && u < n) {
                IntSet neighbors = HashIntSets.newMutableSet();
                if (!line.trim().isEmpty()) {
                    String[] parts = line.split(" ");
                    for (String p : parts) {
                        String[] pair = p.split(",");
                        int v = Integer.parseInt(pair[0]);
                        neighbors.add(v);
                    }
                }
                adj.put(u, neighbors);
                vertices.add(u);
                u++;
            }
            return new Graph(adj, vertices);
        }
    }

    // --- Scorer ---
    public static double edgeVertexRatio(PrimitiveGraphView view) {
        return view.getVertexCount() == 0 ? 0.0 : (double) view.getEdgeCount() / view.getVertexCount();
    }

    // --- Peeling ---
    public static PrimitiveGraphView peel(Graph graph) {
        IntSet currentNodes = HashIntSets.newMutableSet(graph.vertices);
        PrimitiveGraphView bestView = new PrimitiveGraphView(
                currentNodes, graph.adj,
                currentNodes::contains, (u, v) -> currentNodes.contains(u) && currentNodes.contains(v)
        );
        double bestScore = edgeVertexRatio(bestView);

        while (!currentNodes.isEmpty()) {
            int bestVertex = findBestVertexToRemove(graph, currentNodes);
            if (bestVertex == -1) break;

            currentNodes.removeInt(bestVertex);
            PrimitiveGraphView newView = new PrimitiveGraphView(
                    currentNodes, graph.adj,
                    currentNodes::contains, (u, v) -> currentNodes.contains(u) && currentNodes.contains(v)
            );
            double newScore = edgeVertexRatio(newView);

            if (newScore > bestScore) {
                bestScore = newScore;
                bestView = newView;
            }
            System.out.println("BestVertex=" + bestVertex + " BestScore=" + bestScore + " BestNumVertices=" + bestView.vertexCount +
                    " NumVertices=" + currentNodes.size());
        }
        return bestView;
    }

    // --- Parallel Best Vertex ---
    private static int findBestVertexToRemove(Graph graph, IntSet currentNodes) {
        AtomicReference<Integer> bestVertex = new AtomicReference<>(-1);
        AtomicReference<Double> bestScore = new AtomicReference<>(edgeVertexRatio(
                new PrimitiveGraphView(currentNodes, graph.adj, currentNodes::contains, (u, v) -> currentNodes.contains(u) && currentNodes.contains(v))));

        currentNodes.parallelStream().forEach(v -> {
            IntPredicate exclude = x -> currentNodes.contains(x) && x != v;
            IntIntPredicate edgePred = (u, w) -> exclude.test(u) && exclude.test(w);
            PrimitiveGraphView view = new PrimitiveGraphView(currentNodes, graph.adj, exclude, edgePred);
            double score = edgeVertexRatio(view);
            synchronized (bestScore) {
                if (score > bestScore.get()) {
                    bestScore.set(score);
                    bestVertex.set(v);
                }
            }
        });

        return bestVertex.get();
    }

    // --- Main ---
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java GreedyPeeling <graph_dir>");
            System.exit(1);
        }

        Graph graph = Graph.loadFromDirectory(args[0]);
        PrimitiveGraphView best = peel(graph);

        System.out.printf("Best score (edge/vertex): %.4f\n", edgeVertexRatio(best));
    }
}