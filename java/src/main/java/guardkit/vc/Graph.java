package guardkit.vc;

import java.util.ArrayList;
import java.util.List;

/** An immutable undirected graph on vertices 0..n-1. */
public final class Graph {
    public final int n;
    public final int[][] edges;   // each edge {u, v}, u != v
    public final int[][] adj;

    public Graph(int n, int[][] edgeList) {
        this.n = n;
        List<int[]> es = new ArrayList<>();
        for (int[] e : edgeList) if (e[0] != e[1]) es.add(e);
        this.edges = es.toArray(new int[0][]);
        int[] deg = new int[n];
        for (int[] e : edges) { deg[e[0]]++; deg[e[1]]++; }
        adj = new int[n][];
        for (int v = 0; v < n; v++) adj[v] = new int[deg[v]];
        int[] fill = new int[n];
        for (int[] e : edges) { adj[e[0]][fill[e[0]]++] = e[1]; adj[e[1]][fill[e[1]]++] = e[0]; }
    }
}
