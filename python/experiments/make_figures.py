"""Generates the paper's figures (PDF) from results/*.json."""
import json, math, sys
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch

plt.rcParams.update({"font.size": 9, "font.family": "serif", "axes.spines.top": False, "axes.spines.right": False})
R = lambda name: json.load(open(f"results/{name}.json"))
OUT = sys.argv[1] if len(sys.argv) > 1 else "figures"

# ---------------------------------------------------------------- Fig. 1: structure of guarded dispatch
fig, ax = plt.subplots(figsize=(6.6, 3.0))
ax.set_xlim(0, 100); ax.set_ylim(0, 46); ax.axis("off")

def box(x, y, w, h, text, fc="#ffffff", ec="#333333", bold=False):
    ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.4,rounding_size=1.2", fc=fc, ec=ec, lw=0.9))
    ax.text(x + w / 2, y + h / 2, text, ha="center", va="center", fontsize=8, weight="bold" if bold else "normal")

def arrow(x1, y1, x2, y2, label=None, dy=1.2):
    ax.annotate("", xy=(x2, y2), xytext=(x1, y1), arrowprops=dict(arrowstyle="-|>", lw=0.8, color="#333333"))
    if label:
        ax.text((x1 + x2) / 2, (y1 + y2) / 2 + dy, label, ha="center", fontsize=7, color="#444444")

box(1, 33, 11, 8, "request\n$x$", fc="#f2f2f2")
box(17, 33, 20, 8, "ordering policy\n(ratio $p_i/v_i$)", fc="#eef3fb")
arrow(12.6, 37, 16.4, 37)
arrow(37.6, 37, 43.4, 37)
gy = [33, 19, 5]
for i, y in enumerate(gy, 1):
    box(44, y, 18, 8, f"guard $K_{i}$\ncheck, cost $v_i$", fc="#fff6e8")
    box(70, y, 18, 8, f"solver for $K_{i}$\ncontract bound", fc="#eaf6ea")
    arrow(62.6, y + 4, 69.4, y + 4, "match")
for a_, b_ in [(gy[0], gy[1]), (gy[1], gy[2])]:
    arrow(53, a_ - 0.6, 53, b_ + 8.6)
    ax.text(54.2, (a_ + b_ + 8) / 2, "no match", fontsize=6.5, color="#444444", va="center")
box(10, 5, 20, 8, "admission policy\nfallback or reject", fc="#fbeaea")
arrow(43.4, 9, 30.6, 9)
ax.text(37.2, 12.3, "no guard\nmatched", ha="center", fontsize=6.2, color="#444444")
ax.annotate("", xy=(27, 32.4), xytext=(43.4, 23), arrowprops=dict(arrowstyle="-|>", lw=0.7, ls="--", color="#777777"))
ax.text(14, 24.5, "observed hit rates\nand check costs", fontsize=6.5, color="#666666", ha="center")
ax.text(91, 22, "latency bound\n$=\\sum$ guard costs\n+ solver bound", fontsize=7, va="center", color="#333333")
fig.savefig(f"{OUT}/fig1_structure.pdf", bbox_inches="tight"); plt.close(fig)

# ---------------------------------------------------------------- Fig. 2: effectiveness (E1)
e1 = R("e1_effectiveness")["rows"]
fig, axes = plt.subplots(1, 3, figsize=(7.0, 3.3))
for ax, dom in zip(axes, ["Partition", "SAT", "Vertex Cover"]):
    fams = []
    for r in e1:
        if r["domain"] == dom and r["family"] not in fams:
            fams.append(r["family"])
    for j, fam in enumerate(fams):
        rows = [r for r in e1 if r["domain"] == dom and r["family"] == fam]
        xs = [r["n"] for r in rows]; ys = [max(r["guarded_total"]["median"], 1e-7) for r in rows]
        line, = ax.plot(xs, ys, marker="o", ms=3, lw=1, label=f"{fam}: guarded")
        fr = [r for r in rows if r["fallback"]]
        if fr:
            fx = [r["n"] for r in fr]
            fy = [r["fallback"]["median"] if r["fallback"]["median"] != float("inf") else 10.0 for r in fr]
            ax.plot(fx, fy, marker="s", ms=3, lw=1, ls="--", color=line.get_color(), label=f"{fam}: fallback")
            for x, r in zip(fx, fr):
                if r["fallback"]["median"] == float("inf"):
                    ax.annotate("timeout\n(>10 s)", (x, 10.0), fontsize=5.5, xytext=(-14, 5), textcoords="offset points")
    ax.set_xscale("log"); ax.set_yscale("log"); ax.set_title(dom, fontsize=9)
    ax.set_xlabel("input size $n$"); ax.grid(alpha=0.25, lw=0.5)
    ax.legend(fontsize=5.6, frameon=False, loc="upper center", bbox_to_anchor=(0.5, -0.24), ncol=1)
    ax.set_ylim(top=60)
axes[0].set_ylabel("median time (s)")
fig.tight_layout(); fig.savefig(f"{OUT}/fig2_effectiveness.pdf", bbox_inches="tight"); plt.close(fig)

# ---------------------------------------------------------------- Fig. 3: guard ordering (E3)
e3 = R("e3_ordering")["rows"]
fig, ax = plt.subplots(figsize=(5.2, 2.2))
labels = [r["policy"] for r in e3]; vals = [r["mean_verify"] * 1e3 for r in e3]
bars = ax.barh(range(len(vals)), vals, color=["#9bb7d4", "#d49b9b", "#c9c9c9", "#7fb07f", "#5f9f5f"])
ax.set_yticks(range(len(vals))); ax.set_yticklabels(labels, fontsize=7.5); ax.invert_yaxis()
ax.set_xlabel("mean guard verification time per request (ms)")
for b, v in zip(bars, vals):
    ax.text(v, b.get_y() + b.get_height() / 2, f" {v:.3f}", va="center", fontsize=7)
fig.tight_layout(); fig.savefig(f"{OUT}/fig3_ordering.pdf", bbox_inches="tight"); plt.close(fig)

# ---------------------------------------------------------------- Fig. 4: robustness (E4)
e4 = R("e4_robustness")
rhos = [float(r) for r in e4["misestimation"]]
mean = [e4["misestimation"][r]["mean"] for r in e4["misestimation"]]
mx = [e4["misestimation"][r]["max"] for r in e4["misestimation"]]
fig, ax = plt.subplots(figsize=(3.6, 2.4))
ax.plot(rhos, rhos, ls=":", color="#888888", label="guaranteed bound $\\rho$")
ax.plot(rhos, mx, marker="^", ms=4, lw=1, label="worst observed")
ax.plot(rhos, mean, marker="o", ms=4, lw=1, label="mean")
ax.set_xlabel("cost misestimation factor $\\rho$"); ax.set_ylabel("cost / optimum")
ax.set_xscale("log", base=2); ax.legend(fontsize=7, frameon=False); ax.grid(alpha=0.25, lw=0.5)
fig.tight_layout(); fig.savefig(f"{OUT}/fig4_robustness.pdf", bbox_inches="tight"); plt.close(fig)
print("figures written to", OUT)
