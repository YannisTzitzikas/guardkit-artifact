"""E0: differential testing of every specialized solver and fallback against brute force."""
import json, random, sys
sys.path.insert(0, ".")
from guardkit import partition as P, sat as S, vc as V

rng = random.Random(2026)
res = {}

# Partition
ok = bad = 0
for t in range(3000):
    n = rng.randint(1, 14)
    kind = t % 4
    if kind == 0: s = [rng.randint(1, 9)] * n
    elif kind == 1: s = list(range(1, n + 1)); rng.shuffle(s)
    elif kind == 2: s = [rng.randint(1, 30) for _ in range(n)]
    else: s = [rng.randint(1, 10**6) for _ in range(n)]
    ref = P.brute_force(s)
    checks = [P.solve_mitm(s) == ref]
    if P.is_all_equal(s): checks.append(P.solve_all_equal(s) == ref)
    if P.is_arith(s): checks.append(P.solve_arith(s) == ref)
    if P.is_small_total(s): checks.append(P.solve_small_total(s) == ref)
    ok += all(checks); bad += not all(checks)
res["partition"] = {"instances": ok + bad, "disagreements": bad}

# SAT
ok = bad = 0; hits = {"2-CNF": 0, "Horn": 0, "backdoor": 0}
for t in range(2000):
    n = rng.randint(3, 11)
    kind = t % 4
    if kind == 0: f = S.gen_2cnf(n, rng.randint(1, 3 * n), rng)
    elif kind == 1: f = S.gen_horn(n, rng.randint(1, 3 * n), rng)
    elif kind == 2: f = S.gen_backdoor(n + 3, rng.randint(1, 3 * n), rng.randint(1, 3), rng)
    else: f = S.gen_random_3cnf(n, rng.choice([2.0, 4.26, 6.0]), rng)
    ref = S.brute_force(f)
    checks = [S.solve_dpll(f) == ref]
    if S.is_2cnf(f): checks.append(S.solve_2cnf(f) == ref); hits["2-CNF"] += 1
    if S.is_horn(f): checks.append(S.solve_horn(f) == ref); hits["Horn"] += 1
    if S.has_small_backdoor(f, k=4):
        B = f.backdoor
        # the backdoor must hit every clause with three distinct variables
        checks.append(all(len({abs(l) for l in c}) < 3 or set(B) & {abs(l) for l in c} for c in f.clauses))
        checks.append(S.solve_backdoor(f) == ref); hits["backdoor"] += 1
    ok += all(checks); bad += not all(checks)
res["sat"] = {"instances": ok + bad, "disagreements": bad, "guard_hits": hits}

# Vertex Cover
ok = bad = 0; hits = {"forest": 0, "bipartite": 0}
for t in range(1500):
    n = rng.randint(2, 13)
    kind = t % 3
    if kind == 0: g = V.gen_tree(n, rng)
    elif kind == 1: g = V.gen_bipartite(n, rng.uniform(1, 3), rng)
    else: g = V.gen_random(n, rng.uniform(1, 4), rng)
    ref = V.brute_force(g)
    checks = [V.solve_bnb(g) == ref]
    if V.is_forest(g): checks.append(V.solve_forest(g) == ref); hits["forest"] += 1
    if V.is_bipartite(g): checks.append(V.solve_bipartite(g) == ref); hits["bipartite"] += 1
    ok += all(checks); bad += not all(checks)
res["vertex_cover"] = {"instances": ok + bad, "disagreements": bad, "guard_hits": hits}

print(json.dumps(res, indent=1))
json.dump(res, open("results/e0_correctness.json", "w"), indent=1)
