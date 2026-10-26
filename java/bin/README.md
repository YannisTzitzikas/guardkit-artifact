# guardkit (Java)

Java port of **guardkit**, the artifact of the paper
*Guarded Dispatch with Complexity Contracts: Exploiting Checkable Input Structure in Software that Solves Hard Problems*.

A **guard** couples a checkable property K of the input with a solver that is correct and efficient on inputs
satisfying K, together with a **contract** stating the cost of checking K and of solving under K. A **dispatcher**
evaluates guards in an order chosen to minimize expected checking cost, runs the solver of the first match, and
otherwise applies an explicit admission policy (run the fallback, or throw `Rejected`).

The library has no dependencies beyond the JDK (Java 17 or later).

## Contents

| Package | Contents |
|---|---|
| `guardkit` | `Guard` (predicate and certifying), `ComplexityContract`, `Dispatcher`, `OrderPolicy` (`DECLARED`, `RATIO_STATIC`, `RATIO_ADAPTIVE`, `RANDOM`), `Outcome`, `GuardCheck`, `Rejected`, `Ordering` (expected checking cost, ratio rule) |
| `guardkit.partition` | Partition: all-equal, arithmetic-sequence and small-total guards; meet-in-the-middle fallback |
| `guardkit.sat` | SAT: 2-CNF (SCC), Horn (forward chaining) and strong-backdoor certifying guards with witnesses `TwoCnf`, `HornCnf`, `Backdoor`; DPLL fallback; generators |
| `guardkit.vc` | Vertex Cover: forest (leaf rule) predicate guard and bipartite (Konig + Hopcroft-Karp) certifying guard with witness `TwoColoring`; branch-and-bound fallback; generators |
| `guardkit.examples` | `Example`: minimal usage |
| `guardkit.experiments` | `Correctness` (differential tests vs. brute force), `Benchmark` (guarded vs. fallback), `Robustness` (guard ordering under misestimated costs) |

## Usage

```java
Dispatcher<long[], Boolean> d = new Dispatcher<>(
    Partition.guards(), Partition::solveMitm, Partition::admit, OrderPolicy.RATIO_ADAPTIVE);
Outcome<Boolean> out = d.solve(input);
System.out.println(out.answer() + " via " + out.branch() + " in " + out.totalTime() + " s");
```

### Two kinds of guard

A **predicate guard** checks the property with a boolean predicate and then solves the original input:

```java
Guard<long[], Boolean> g = new Guard<>("all-equal", Partition::isAllEqual, Partition::solveAllEqual, "O(n)", "O(1)");
```

A **certifying guard** checks the property by producing a typed *witness* that only the check can create
(its constructor is package-private). The specialized solver accepts only the witness type, so it cannot be
applied to an unchecked input (`Sat.solveBackdoor(cnf)` does not compile), and whatever the check found
(here, the backdoor set) reaches the solver without side channels through the input:

```java
Guard<Cnf, Boolean> g = Guard.certifying("backdoor<=4",
        f -> Sat.findBackdoor(f, 4),            // Optional<Backdoor>
        Sat::solveBackdoor,                     // accepts only a Backdoor
        new ComplexityContract("O(3^k m)", "O(2^k (n+m))"));
```

The SAT guards (`TwoCnf`, `HornCnf`, `Backdoor`) and the bipartite Vertex Cover guard (`TwoColoring`) are
certifying guards; the Partition guards and the forest guard are predicate guards. Both kinds can be mixed
in one dispatcher. Inputs (`Cnf`, `Graph`) and witnesses are immutable.

Defining your own predicate guard:

```java
Guard<MyInput, MyResult> g = new Guard<>("my-property", MyChecks::holds, MySolvers::fastSolve, "O(n)", "O(n log n)")
        .withPriors(0.3, 2e-6);   // optional: prior hit rate and check cost (seconds) for RATIO_STATIC
```

## Build and run

With Maven:
```sh
mvn -q compile
java -cp target/classes guardkit.experiments.Correctness
java -cp target/classes guardkit.examples.Example
java -cp target/classes guardkit.experiments.Benchmark
java -cp target/classes guardkit.experiments.Robustness
```

Without Maven:
```sh
mkdir -p out && javac --release 17 -d out $(find src -name '*.java')
java -cp out guardkit.experiments.Correctness
```

## Results

`results/` contains the output of the three programs on the machine used for development
(OpenJDK 21, one core of an Intel Xeon at 2.80 GHz). The differential tests pass on all 6,500 instances and also validate the witnesses (every backdoor hits all clauses with three variables; every 2-colouring is proper).

The timings in the paper were measured with the Python implementation; the Java timings are not
comparable to them. `Benchmark` is a simple harness without JIT warm-up, so very small times are noisy;
use JMH for rigorous Java measurements.

## Notes

- Guards never cache results across requests: every request pays its own checking cost. Information found by a
  check reaches the solver through the witness, never through the input object.
- The dispatcher is single-threaded; for concurrent use, protect the per-guard statistics (the only mutable state).
- `Benchmark` runs the solvers in a thread with a 1 GB stack, because DPLL and branch-and-bound are recursive.
