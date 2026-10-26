package guardkit.vc;

/**
 * Witness that a graph is bipartite: a proper 2-colouring of its vertices. Instances are created
 * only by {@link VertexCover#twoColor(Graph)}, so the bipartite solver cannot be applied to a graph
 * whose bipartiteness has not been checked.
 */
public final class TwoColoring {
    private final Graph graph;
    private final int[] color;

    TwoColoring(Graph graph, int[] color) {
        this.graph = graph;
        this.color = color;
    }

    public Graph graph() { return graph; }
    public int color(int v) { return color[v]; }
}
