"""E2: overhead of failed guards on unstructured inputs, relative to the fallback solver."""
import random, sys
sys.path.insert(0, ".")
from experiments.common import timed, summary, save
from guardkit import Dispatcher, Guard, partition as P, sat as S, vc as V

REPS = 10
rng = random.Random(7)
out = {"rows": []}

def run(domain, family, sizes, gen, guards, fallback, limit=20.0):
    d = Dispatcher(guards, fallback=fallback, admit=lambda x: True)
    for n in sizes:
        ver, fb, per_guard = [], [], {}
        for _ in range(REPS):
            x = gen(n)
            o = d.solve(x)
            assert o.branch == "fallback", (domain, family, n, o.branch)
            ver.append(o.verify_time); fb.append(o.solve_time)
            for name, dt, hit in o.guard_log:
                per_guard.setdefault(name, []).append(dt)
        ratio = [v / f for v, f in zip(ver, fb)]
        row = {"domain": domain, "family": family, "n": n, "guards_total": summary(ver),
               "fallback": summary(fb), "overhead_ratio": summary(ratio),
               "per_guard_median": {k: summary(v)["median"] for k, v in per_guard.items()}}
        out["rows"].append(row)
        print(domain, family, n, "guards %.2e s" % row["guards_total"]["median"],
              "fallback %.2e s" % row["fallback"]["median"],
              "overhead %.3f%%" % (100 * row["overhead_ratio"]["median"]), flush=True)

def even_total(n):
    # random large values with an even total, so the fallback cannot answer from parity alone
    s = [rng.randint(10**8, 10**9) for _ in range(n)]
    if sum(s) % 2:
        s[-1] += 1
    return s
run("Partition", "random large values (even total)", [20, 28, 36, 40, 44], even_total, P.guards(), P.solve_mitm)
run("SAT", "random 3-CNF (ratio 4.26)", [30, 40, 50, 60],
    lambda n: S.gen_random_3cnf(n, 4.26, rng), S.guards(), S.solve_dpll)
budgeted = [g for g in S.guards() if g.name != "backdoor<=k"] + [
    Guard("backdoor<=4", lambda f: S.has_small_backdoor(f, 4), S.solve_backdoor, "O(3^4 m)", "O(2^4 (n+m))")]
run("SAT", "random 3-CNF (ratio 4.26), budget k=4", [30, 40, 50, 60],
    lambda n: S.gen_random_3cnf(n, 4.26, rng), budgeted, S.solve_dpll)
run("Vertex Cover", "random graph (avg deg 6)", [40, 60, 80, 100],
    lambda n: V.gen_random(n, 6, rng), V.guards(), V.solve_bnb)
save("e2_overhead", out)
print("done")
