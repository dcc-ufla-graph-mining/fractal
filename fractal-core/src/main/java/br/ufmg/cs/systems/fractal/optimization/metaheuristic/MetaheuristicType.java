package br.ufmg.cs.systems.fractal.optimization.metaheuristic;

public enum MetaheuristicType {
    VNS("Variable Neighborhood Search"),    // Variable Neighborhood Search
    ILS("Iterated Local Search"),    // Iterated Local Search
    TS("Tabu Search");  // Tabu Search

    private final String fullName;

    MetaheuristicType(String fullName) {
        this.fullName = fullName;
    }

    public String getFullName() {
        return fullName;
    }
}
