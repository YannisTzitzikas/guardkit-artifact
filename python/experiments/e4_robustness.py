"""E4: guard ordering under misestimated verification costs (synthetic, exact computation)."""
import itertools, math, random, sys
sys.path.insert(0, ".")
from experiments.common import save
from guardkit import expected_verification_cost, ratio_order

rng = random.Random(11)
TRIALS = 2000
M = 6                       # guards; small enough to brute-force the optimum over all M! orders
RHOS = [1.0, 1.5, 2.0, 4.0, 8.0]

def instance():
    names = [f"g{i}" for i in range(M)]
    w = [rng.expovariate(1.0) for _ in range(M + 1)]          # last weight = no guard matches
    tot = sum(w)
    p = {s: w[i] / tot for i, s in enumerate(names)}
    v = {s: math.exp(rng.uniform(math.log(1e-6), math.log(1e-2))) for s in names}  # 1 us .. 10 ms
    return names, p, v

def optimum(names, p, v):
    return min(expected_verification_cost(list(o), p, v) for o in itertools.permutations(names))

out = {"rows": [], "trials": TRIALS, "guards": M}
ratio_rule_gap = 0.0
heur = {"ratio rule": [], "cheapest first": [], "most likely first": [], "random": []}
per_rho = {r: [] for r in RHOS}
for _ in range(TRIALS):
    names, p, v = instance()
    opt = optimum(names, p, v)
    rr = expected_verification_cost(ratio_order(names, p, v), p, v)
    ratio_rule_gap = max(ratio_rule_gap, rr / opt - 1)
    heur["ratio rule"].append(rr / opt)
    heur["cheapest first"].append(expected_verification_cost(sorted(names, key=v.get), p, v) / opt)
    heur["most likely first"].append(expected_verification_cost(sorted(names, key=p.get, reverse=True), p, v) / opt)
    perm = names[:]; rng.shuffle(perm)
    heur["random"].append(expected_verification_cost(perm, p, v) / opt)
    for rho in RHOS:   # estimates v_hat with v <= v_hat <= rho * v
        vh = {s: v[s] * rng.uniform(1.0, rho) for s in names}
        per_rho[rho].append(expected_verification_cost(ratio_order(names, p, vh), p, v) / opt)

def stats(xs):
    xs = sorted(xs)
    return {"mean": sum(xs) / len(xs), "p95": xs[int(0.95 * len(xs)) - 1], "max": xs[-1]}

out["ratio_rule_max_relative_gap_vs_bruteforce_optimum"] = ratio_rule_gap
out["heuristics"] = {k: stats(v) for k, v in heur.items()}
out["misestimation"] = {str(r): dict(stats(per_rho[r]), bound=r) for r in RHOS}
save("e4_robustness", out)
print("ratio rule vs brute-force optimum, max relative gap:", ratio_rule_gap)
for k, s in out["heuristics"].items(): print("%-18s mean %.3f p95 %.3f max %.3f" % (k, s["mean"], s["p95"], s["max"]))
for r, s in out["misestimation"].items(): print("rho=%-4s mean %.3f p95 %.3f max %.3f (bound %s)" % (r, s["mean"], s["p95"], s["max"], r))
