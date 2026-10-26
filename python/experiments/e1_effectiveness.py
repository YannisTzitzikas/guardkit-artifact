"""E1: guarded dispatch vs the general fallback solver on structured inputs."""
import random, sys
sys.path.insert(0, ".")
from experiments.common import timed, summary, save
from guardkit import Dispatcher, partition as P, sat as S, vc as V

REPS = 10
LIMIT = 10.0
rng = random.Random(42)
out = {"rows": []}

def run(domain, family, sizes, gen, guards, fallback, fallback_sizes):
    d = Dispatcher(guards, fallback=None)
    for n in sizes:
        g_tot, g_ver, f_tot, branches = [], [], [], set()
        for _ in range(REPS):
            x = gen(n)
            o = d.solve(x)
            g_tot.append(o.total_time); g_ver.append(o.verify_time); branches.add(o.branch)
            if n in fallback_sizes:
                t, r = timed(fallback, x, limit=LIMIT)
                f_tot.append(t)
                if t != float("inf"):
                    assert r == o.answer, (domain, family, n)
        row = {"domain": domain, "family": family, "n": n, "branch": sorted(branches),
               "guarded_total": summary(g_tot), "guarded_verify": summary(g_ver),
               "fallback": summary(f_tot) if f_tot else None}
        out["rows"].append(row)
        print(domain, family, n, row["branch"], "guarded med %.4f" % row["guarded_total"]["median"],
              "fallback med", None if not f_tot else ("%.4f" % row["fallback"]["median"]), flush=True)

# Partition: values are large, so the small-total guard cannot apply
big = lambda: rng.randint(10**8, 10**9)
small_n = [20, 28, 36, 40, 44]
large_n = [10**3, 10**4, 10**5, 10**6]
run("Partition", "all-equal", small_n + large_n, lambda n: [big()] * n,
    P.guards(), P.solve_mitm, set(small_n))
def arith(n):
    s = list(range(1, n + 1)); rng.shuffle(s); return s
run("Partition", "arithmetic", small_n + large_n, arith, P.guards(), P.solve_mitm, set(small_n))

# SAT
run("SAT", "2-CNF (m=2n)", [100, 1000, 10000, 100000], lambda n: S.gen_2cnf(n, 2 * n, rng),
    S.guards(), S.solve_dpll, {100, 1000, 10000})
run("SAT", "Horn (m=3n)", [100, 1000, 10000, 100000], lambda n: S.gen_horn(n, 3 * n, rng),
    S.guards(), S.solve_dpll, {100, 1000, 10000})
run("SAT", "backdoor k=6 (m=3n)", [60, 120, 240, 480], lambda n: S.gen_backdoor(n, 3 * n, 6, rng),
    S.guards(), S.solve_dpll, {60, 120, 240, 480})

# Vertex Cover
run("Vertex Cover", "forest", [100, 1000, 10000, 100000], lambda n: V.gen_tree(n, rng),
    V.guards(), V.solve_bnb, {100, 1000})
run("Vertex Cover", "bipartite (avg deg 6)", [50, 100, 200, 400, 10000, 100000],
    lambda n: V.gen_bipartite(n, 6, rng), V.guards(), V.solve_bnb, {50, 100, 200, 400})

save("e1_effectiveness", out)
print("done")
