# guardkit artifact

Artifact of *Guarded Dispatch with Complexity Contracts: Exploiting Checkable Input Structure in Software that Solves Hard Problems*.

- `python/` - the reference implementation used for all measurements in the paper, with the experiment
  scripts (E0-E4), raw results and figure generation. Run `./run_all.sh` to reproduce everything.
- `java/` - the Java 17 implementation of the object-oriented refinement (certifying guards with typed
  witnesses, complexity contracts as records), with differential tests, a benchmark and the robustness study.
  See `java/README.md`.
