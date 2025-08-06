# An Experimental Study of Variable Neighborhood Search for General-Purpose Subgraph Optimization in Parallel Systems (SSCAD 2025)

## Reproducibility Guide

This document provides instructions to reproduce the experiments from the paper

### Environment Variables
Please ensure that the environment variables are correctly exported as described in README-fractal.md:

- ```JAVA_HOME``` points to an OpenJDK 8 installation  
- ```SPARK_HOME``` points to your Apache Spark installation  
- ```FRACTAL_HOME``` points to the root directory of the Fractal system

### Setup Instructions

1. Clone the Fractal repository (branch ```sscad2025```):
    ```
    git clone -b sscad2025 git@github.com:dcc-ufla-graph-mining/fractal.git
    ```

2. Build the system by following the steps in ```$FRACTAL_HOME/README-fractal.md```.

3.  Download [input graphs from GDrive](https://drive.google.com/drive/folders/1VLs8mpsqono2Q6FWq4Fj13grKLZ2uJSN) and place the content on ```$FRACTAL_HOME_```

### Running the System

To run the system, use the following command:

```
./gradlew jar && \
master_memory=<master_memory> \
app_class=br.ufmg.cs.systems.fractal.apps.VNSApp \
worker_cores=<worker_cores> \
args="<input_graph> <vertices> <solutions> <seed> <time_limit> <objective_function> [graph_label_type]" \
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
- ```<objective_function>```: Name of the objective function to be optimized (defined on VNSApp class)
- ```<graph_label_type>``` *(optional)*: Type of labeling used in the graph; accepted values:
  - `unlabeled` (default)
  - `vertexlabeled`
  - `vertexedgelabeled`


Implemented objective functions include:
- `conductance` (Conductance)
- `densesubgraph` (Densest Subgraph)
- `degreeentropy` (Degree Entropy)
- `labelentropy` (Label Entropy)
- `triangledensestsubgraph` (Triangle Densest Subgraph)

#### Example of execution command:

```
./gradlew jar && \
master_memory=20g \
app_class=br.ufmg.cs.systems.fractal.apps.VNSApp \
worker_cores=16 \
args="data/dblp 10 100 -1 1000 densesubgraph unlabeled" \
./bin/fractal-custom-app.sh
```

This example runs the `Densest Subgraph` objective function on the `dblp` graph, using 16 cores, with 100 initial solutions of size 10, a random seed, a 1-second time limit per solution and assumes the graph has no labels.
