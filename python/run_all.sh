
#!/bin/sh
# Reproduces every result and figure of the paper (Python >= 3.8, matplotlib for the figures).
set -e
mkdir -p results figures
python3 experiments/e0_correctness.py
python3 experiments/e1_effectiveness.py
python3 experiments/e2_overhead.py
python3 experiments/e3_ordering.py
python3 experiments/e4_robustness.py
python3 experiments/make_figures.py figures
