# guardkit

Predicate-based algorithm dispatch with complexity contracts: the artifact of the paper
*Guarded Dispatch with Complexity Contracts: Exploiting Checkable Input Structure in Software that Solves Hard Problems*.

A **guard** couples a checkable property K of the input with a solver that is correct and efficient
on inputs satisfying K, together with a **contract** stating the cost of checking K and of solving under K.
A **dispatcher** evaluates guards in an order chosen to minimize expected checking cost, runs the solver of
the first match, and otherwise applies an explicit admission policy (fallback with a size limit, or reject).

## Contents

- `guardkit/core.py` - `Guard`, `Dispatcher` (ordering policies: `declared`, `ratio-static`, `ratio-adaptive`, `random`), outcome records, the expected-cost formula and the ratio rule.
- `guardkit/partition.py` - Partition: all-equal, arithmetic-sequence and small-total guards; meet-in-the-middle fallback.
- `guardkit/sat.py` - SAT: 2-CNF, Horn and strong-backdoor guards; DPLL fallback; instance generators.
- `guardkit/vc.py` - Vertex Cover: forest and bipartite guards; branch-and-bound fallback; generators.
- `experiments/` - E0 (differential correctness), E1 (effectiveness), E2 (overhead), E3 (ordering), E4 (robustness), figure generation.
- `results/` - raw results (JSON) and logs as reported in the paper.

## Usage

```python
from guardkit import Dispatcher, Guard, partition as P

d = Dispatcher(P.guards(), fallback=P.solve_mitm, admit=P.admit, order="ratio-adaptive")
out = d.solve([7] * 1_000_000)
print(out.answer, out.branch, out.verify_time, out.solve_time)
```

## Reproducing the paper

```sh
di    # about 15-20 minutes on one core; requires Python >= 3.8 and matplotlib
```

The library itself uses only the Python standard library. Timings depend on the machine;
the results in `results/` were obtained with CPython 3.12.3 on one core of an Intel Xeon at 2.80 GHz.
