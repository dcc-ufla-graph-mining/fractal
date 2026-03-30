# An Experimental Study of Metaheuristics based on Neighborhood for General-Purpose Subgraph Optimization in Parallel Systems (CCPE 2026)

## Reproducibility Guide

This document provides instructions to reproduce the experiments from the paper

### Environment Variables
Please ensure that the environment variables are correctly exported as described in README-fractal.md:

- ```JAVA_HOME``` points to an OpenJDK 8 installation  
- ```SPARK_HOME``` points to your Apache Spark installation  
- ```FRACTAL_HOME``` points to the root directory of the Fractal system

### Setup Instructions

1. Clone the Fractal repository (branch ```CCPE2026```):
    ```
    git clone -b CCPE2026 git@github.com:dcc-ufla-graph-mining/fractal.git
    ```

2. Build the system by following the steps in ```$FRACTAL_HOME/README-fractal.md```.

3.  Download [input graphs from GDrive](https://drive.google.com/drive/folders/1VLs8mpsqono2Q6FWq4Fj13grKLZ2uJSN)

### Running the System

To run the system using ILS or VNS metaheuristics, use the following command:

```
./gradlew jar && \
app_class=br.ufmg.cs.systems.fractal.apps.SubgraphOptimizationApp \
master_memory=<master_memory> \
worker_cores=<worker_cores> \
args="<input_graph> <vertices> <solutions> <seed> <time_limit> <objective_function> <metaheuristic> <graph_label_type>" \
./bin/fractal-custom-app.sh
```

To run the system using TS metaheuristic, use the following command:

```
./gradlew jar && \
app_class=br.ufmg.cs.systems.fractal.apps.SubgraphOptimizationApp \
master_memory=<master_memory> \
worker_cores=<worker_cores> \
args="<input_graph> <vertices> <solutions> <seed> <time_limit> <objective_function> ts <graph_label_type> <tabu_list_length>" \
./bin/fractal-custom-app.sh
```

#### Parameters:

- ```<master_memory>```: Maximum memory allowed for the coordinator node (e.g., 8g, 16g)
- ```<worker_cores>```: Number of processing threads (virtual cores) to be used
- ```<input_graph>```: Path to the input graph directory (e.g., data/dblp)
- ```<vertices>```: Number of vertices for each initial solution (subgraph)
- ```<solutions>```: Number of initial solutions to generate
- ```<seed>```: Seed used to generate initial solutions (-1 indicates a random seed)
- ```<time_limit>```: Time limit (in milliseconds) for each VNS run
- ```<objective_function>```: Name of the scoring function to be optimized (defined on SubgraphOptimizationApp class)
- ```<metaheuristic>```: Metaheuristic algorithm to be used in the optimization; accepted values:
  - `vns`
  - `ils`
  - `ts`
- ```<graph_label_type>```: Type of labeling used in the graph; accepted values:
  - `unlabeled`
  - `vertexlabeled`
  - `vertexedgelabeled`
- ```<tabu_list_length>``` *(required only for ts)*: Length of the Tabu List (tabu tenure) used in the optimization process of Tabu Search algorithm.

Implemented objective functions include:
- `conductance` (Conductance)
- `densesubgraph` (Densest Subgraph)
- `degreeentropy` (Degree Entropy)
- `labelentropy` (Label Entropy)
- `triangledensestsubgraph` (Triangle Densest Subgraph)

#### Example of execution command:

```
./gradlew jar && \
app_class=br.ufmg.cs.systems.fractal.apps.SubgraphOptimizationApp \
master_memory=16g \
worker_cores=8 \
args="data/dblp 10 100 -1 2000 densesubgraph vns unlabeled" \
./bin/fractal-custom-app.sh
```

This example runs the VNS metaheuristic for optimizing the `Densest Subgraph` objective function on the `DBLP` graph, using 16g of memory and `8 cores`, generating `100 initial solutions` of `size 10`, using `random seed`, a `2000-milliseconds time limit` per solution and assumes the graph has `no labels`.

### Experiment Scripts

The repository includes automation scripts to reproduce all experiments from the paper:

#### Optimality Experiments
```bash
# Usage: ./scripts/run-scripts/run_optimality.sh [metaheuristic] [graph_label_type] [graph_directory] [log_directory]
./scripts/run-scripts/run_optimality.sh vns vertexlabeled $HOME/graphs-data/youtube logs/optimality
```

#### Scalability Experiments
```bash
# Usage: ./scripts/run-scripts/run_scalability.sh [metaheuristic] [graph_label_type] [graph_directory] [log_directory]
./scripts/run-scripts/run_scalability.sh ils unlabeled $HOME/graphs-data/livejournal logs/scalability
```

#### CPU Performance Experiments
```bash
# Usage: ./scripts/run-scripts/run_so_metrics.sh [metaheuristic] [graph_label_type] [graph_directory] [log_directory]
./scripts/run-scripts/run_so_metrics.sh vns vertexlabeled $HOME/graphs-data/citeseer logs/cpu_performance
```

#### Profiling Experiments
```bash
# Usage: ./scripts/run-scripts/run_profiling.sh [metaheuristic] [graph_label_type] [graph_directory] [log_directory]
./scripts/run-scripts/run_profiling.sh vns vertexlabeled $HOME/graphs-data/dblp logs/profiling
```

Each script generates compressed log files in the given log directory with the output data for analysis.

#### Run All Experiments
To run all experiments for multiple graphs sequentially, use the following command:

```bash
./scripts/run_experiments_scripts.sh
```
