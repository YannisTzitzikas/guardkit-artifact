"""E3: guard ordering policies on a mixed SAT workload (real guards, real timings)."""
import random, statistics, sys
sys.path.insert(0, ".")
from experiments.common import save
from guardkit import Dispatcher, Guard, Rejected, sat as S

N = 100
K = 4
REQUESTS = 400
CAL = 60
rng = random.Random(5)
mix = [("2-CNF", 0.15), ("Horn", 0.30), ("backdoor", 0.45), ("random 3-CNF", 0.10)]

def draw(kind):
    if kind == "2-CNF": return S.gen_2cnf(N, 2 * N, rng)
    if kind == "Horn": return S.gen_horn(N, 3 * N, rng)
    if kind == "backdoor": return S.gen_backdoor(N, 3 * N, K, rng)
    return S.gen_random_3cnf(40, 4.26, rng)

def workload(size):
    kinds = rng.choices([k for k, _ in mix], weights=[w for _, w in mix], k=size)
    return [draw(k) for k in kinds]

def make_guards():
    return [Guard("2-CNF", S.is_2cnf, S.solve_2cnf, "O(m)", "O(n+m)"),
            Guard("Horn", S.is_horn, S.solve_horn, "O(m)", "O(n+m)"),
            Guard("backdoor<=4", lambda f: S.has_small_backdoor(f, K), S.solve_backdoor, "O(3^k m)", "O(2^k (n+m))")]

# calibration sample: evaluate every guard on every instance to estimate p and v
cal = workload(CAL)
gs = make_guards()
import time
for f in cal:
    for g in gs:
        t0 = time.perf_counter(); hit = g.check(f); dt = time.perf_counter() - t0
        g.n_checks += 1; g.n_hits += bool(hit); g.total_check_time += dt
priors = {g.name: (g.n_hits / g.n_checks, g.total_check_time / g.n_checks) for g in gs}
print("calibration (hit rate, mean check cost):", {k: (round(a, 3), "%.2e" % b) for k, (a, b) in priors.items()})

requests = workload(REQUESTS)
policies = {
    "declared (2-CNF, Horn, backdoor)": ("declared", False),
    "worst (backdoor first)": ("declared", "reverse"),
    "random": ("random", False),
    "ratio-static (calibrated)": ("ratio-static", True),
    "ratio-adaptive (no priors)": ("ratio-adaptive", False),
}
out = {"calibration": priors, "n": N, "k": K, "requests": REQUESTS, "mix": mix, "rows": []}
for label, (order, opt) in policies.items():
    guards = make_guards()
    if opt == "reverse":
        guards = guards[::-1]
    if opt is True:
        for g in guards:
            g.prior_hit, g.prior_cost = priors[g.name]
    d = Dispatcher(guards, fallback=S.solve_dpll, admit=S.admit, order=order, explore=0.05, seed=1)
    ver, tot, orders = [], [], {}
    for f in requests:
        o = d.solve(f)
        ver.append(o.verify_time); tot.append(o.total_time)
    first_half, second_half = ver[:REQUESTS // 2], ver[REQUESTS // 2:]
    final_order = [g.name for g in d._ordered()] if order != "random" else None
    row = {"policy": label, "mean_verify": statistics.mean(ver), "mean_total": statistics.mean(tot),
           "mean_verify_first_half": statistics.mean(first_half), "mean_verify_second_half": statistics.mean(second_half),
           "final_order": final_order}
    out["rows"].append(row)
    print("%-34s verify %.2e s  total %.2e s  (1st half %.2e, 2nd half %.2e) order=%s" %
          (label, row["mean_verify"], row["mean_total"], row["mean_verify_first_half"], row["mean_verify_second_half"], final_order), flush=True)
save("e3_ordering", out)
print("done")
