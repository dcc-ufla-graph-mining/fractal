/**
 * Scores a candidate by its number of edges divided by its vertex count.
 *
 * The function reads the candidate without modifying or retaining it.
 * Larger scores are preferred by the framework.
 *
 * @param {Object} subgraph Candidate provided through Java interoperability.
 * @returns {number} A finite density score.
 */
function evaluate(subgraph) {
    var vertices = subgraph.getNumVertices();

    if (vertices === 0) {
        return 0.0;
    }

    return subgraph.getNumEdges() / vertices;
}