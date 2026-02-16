import re

import pandas as pd
import os
import matplotlib.pyplot as plt
import numpy as np
from collections import Counter
from matplotlib.lines import Line2D

# list of files and methods to extract for the plots
filenames = [
    ("results/tsptw/tsptw-2026-02-16_12-56-54-e9cb16a78",
        {
         "MIN_INPUT_AND_OUTPUT_SUM",
         "MIN_DETOUR",
         "ORIGINAL",
         }
     ),
]

plt.rcParams.update({
    'text.usetex': True,

    'font.family': 'serif',
    'font.serif': ['Latin Modern Roman'],
    'font.sans-serif': ['Latin Modern Sans'],

    # Base LaTeX body text = 10pt
    'font.size': 10,           # default text size

    # Axis labels = normalsize (10pt)
    'axes.labelsize': 10,
    'axes.titlesize': 10,

    # Tick labels ~ \small
    'xtick.labelsize': 9,
    'ytick.labelsize': 9,

    # Legend ~ \small
    'legend.fontsize': 9,

    # Figure title (if you use one) ~ \large
    'figure.titlesize': 12,
})


BLACK = '#000000'
ORANGE = '#e69f00'
BLUE = '#0072b2'
BLUISH_GREEN = '#009e73'
VERMILION = '#d55e00'
REDDISH_PURPLE = '#cc79a7'
SKY_BLUE = '#56b4e9'
YELLOW = '#f0e442'

MY_COLORS = [
    BLACK,
    VERMILION,
    BLUISH_GREEN,
    ORANGE,
    BLUE,
    REDDISH_PURPLE,
    SKY_BLUE,
    YELLOW
]

colors = {
    "ORIGINAL": BLACK,
    "MIN_INPUT_AND_OUTPUT_SUM": SKY_BLUE,
    "MEAN_INPUT_AND_OUTPUT_SUM": SKY_BLUE,
    "MIN_DETOUR": BLUISH_GREEN,
    "MST_DETOUR": ORANGE,
    "MST_DETOUR_SHAVING": YELLOW,
    "FORWARD_SLACK": REDDISH_PURPLE,
    "SUBSEQUENCE_SPLIT": VERMILION,
}
naming = {
    "ORIGINAL": "Original",
    "MIN_INPUT_AND_OUTPUT_SUM": "Entrées/Sorties",
    "MEAN_INPUT_AND_OUTPUT_SUM": "I/O Mean",
    "MIN_DETOUR": "Détours",
    "MST_DETOUR": "Restricted detours",
    "MST_DETOUR_SHAVING": "R. detours (shaving)",
    "FORWARD_SLACK": "Slack",
    "SUBSEQUENCE_SPLIT": "Split",
}

global_df = None
all_methods = []
all_best_sols = {}  # best gap per instance
instances_unsat = set()
all_instances = set()
for filename, methods in filenames:
    df = pd.read_csv(filename, engine="python", sep=" \\| ")
    df["instance"] = df["instance"].apply(lambda name: os.path.basename(name))
    # keep only the rows with the asked methods
    df = df[df["variant"].isin(methods)]
    df = df[["instance", "variant", "best_obj", "timeout", "n_nodes", "runtime", "is_completed", "solution_list"]]
    for i, row in df.iterrows():
        instance = row["instance"]
        best_sol = row["best_obj"]
        if instance not in all_best_sols or np.isnan(all_best_sols[instance]):
            all_best_sols[instance] = best_sol
        else:
            all_best_sols[instance] = min(best_sol, all_best_sols[instance])
        if np.isnan(best_sol) and row["is_completed"]:
            instances_unsat.add(instance)
        all_instances.add(instance)
    if global_df is None:
        global_df = df
    else:
        global_df = pd.concat([global_df, df])
    for method in methods:
        all_methods.append(method)
instances_sat = all_instances.difference(instances_unsat)

def cm_to_inch(cm):
    """
    for a more civilized age
    """
    return cm / 2.54

# ========== comparison plot on solved instances ==========

n_methods = len(all_methods)
sorted_methods = sorted(all_methods)
# collect all data per instance
sorted_instances = sorted(instances_sat)
runtime_per_method = {}
search_nodes_per_method = {}

for method in sorted_methods:
    df_method = global_df[global_df["variant"] == method]
    # remove the instances that were proven to be UNSAT
    df_method = df_method[~df_method["instance"].isin(instances_unsat)]
    runtime_list = []
    nodes_list = []
    for instance in sorted_instances:
        row = df_method[df_method["instance"] == instance]
        if row["is_completed"].iloc[0]:
            runtime = df_method[df_method["instance"] == instance]["runtime"].iloc[0]
            nodes = df_method[df_method["instance"] == instance]["n_nodes"].iloc[0]
        else:
            runtime = float("inf")
            nodes = float("inf")
        runtime_list.append(runtime)
        nodes_list.append(nodes)
    runtime_per_method[method] = runtime_list
    search_nodes_per_method[method] = nodes_list

# 1 to 1 comparison for the runtime
fig, axs = plt.subplots(2, n_methods - 1, figsize=(cm_to_inch(7.5), cm_to_inch(7.5)), constrained_layout=True)
sorted_methods_without_original = sorted([m for m in all_methods if m != "ORIGINAL"])
for i in range(n_methods-1):
    method_i = sorted_methods_without_original[i]
    method_j = "ORIGINAL"
    list_for_i_when_i_better = []
    list_for_j_when_i_better = []

    list_for_i_when_j_better = []
    list_for_j_when_j_better = []

    list_for_i_when_equal = []
    list_for_j_when_equal = []
    for v_i, v_j in zip(runtime_per_method[method_j], runtime_per_method[method_i]):
        if np.isinf(v_i) or np.isinf(v_j):
            continue
        if v_i < v_j:
            list_for_i_when_i_better.append(v_i)
            list_for_j_when_i_better.append(v_j)
        elif v_i > v_j:
            list_for_i_when_j_better.append(v_i)
            list_for_j_when_j_better.append(v_j)
        else:
            list_for_i_when_equal.append(v_i)
            list_for_j_when_equal.append(v_j)
    # diagonal line
    axs[(0, i)].plot([0.0, 900.0], [0.0, 900.0], '--', alpha=0.7, color="gray")
    # actual values
    axs[(0, i)].scatter(list_for_j_when_i_better, list_for_i_when_i_better, marker='.', color=VERMILION)
    axs[(0, i)].scatter(list_for_j_when_j_better, list_for_i_when_j_better, marker='.', color=SKY_BLUE)
    axs[(0, i)].scatter(list_for_j_when_equal, list_for_i_when_equal, marker='.', color=BLACK)
    # axs[i].set_xlabel(naming[method_i])
    if i == 0:
        axs[(0, i)].set_ylabel("Temps (s)")

# 1 to 1 comparison for the number of nodes
for i in range(n_methods-1):
    method_i = sorted_methods_without_original[i]
    method_j = "ORIGINAL"
    list_for_i_when_i_better = []
    list_for_j_when_i_better = []

    list_for_i_when_j_better = []
    list_for_j_when_j_better = []

    list_for_i_when_equal = []
    list_for_j_when_equal = []
    max_val = 0
    for v_i, v_j in zip(search_nodes_per_method[method_j], search_nodes_per_method[method_i]):
        if np.isinf(v_i) or np.isinf(v_j):
            continue
        max_val = max(max_val, v_i, v_j)
        if v_i < v_j:
            list_for_i_when_i_better.append(v_i)
            list_for_j_when_i_better.append(v_j)
        elif v_i > v_j:
            list_for_i_when_j_better.append(v_i)
            list_for_j_when_j_better.append(v_j)
        else:
            list_for_i_when_equal.append(v_i)
            list_for_j_when_equal.append(v_j)
    # diagonal line
    axs[(1, i)].plot([0, max_val], [0, max_val], '--', alpha=0.7, color="gray")
    # actual values
    axs[(1, i)].scatter(list_for_j_when_i_better, list_for_i_when_i_better, marker='.', color=VERMILION)
    axs[(1, i)].scatter(list_for_j_when_j_better, list_for_i_when_j_better, marker='.', color=SKY_BLUE)
    axs[(1, i)].scatter(list_for_j_when_equal, list_for_i_when_equal, marker='.', color=BLACK)
    axs[(1, i)].set_xlabel(naming[method_i])
    if i == 0:
        axs[(1, i)].set_ylabel("\# nœuds")

for ax in axs.ravel():
    ax.xaxis.labelpad = 14   # increase gap label <-> ticks/offset

fig.supylabel("Original", x=-0.08)  # move closer to the axes (smaller x => more left)

legend_elements = [
    Line2D([0], [0], marker='o', linestyle='None', color=SKY_BLUE, label="Améliore"),
    Line2D([0], [0], marker='o', linestyle='None', color=VERMILION, label="Détériore"),
    Line2D([0], [0], marker='o', linestyle='None', color=BLACK, label="Identique"),
]

fig.legend(
    handles=legend_elements,
    loc="upper center",
    bbox_to_anchor=(0.5, -0.01),   # centered below the figure
    ncol=3,                       # one row
    frameon=True,
    handletextpad=0.6,
    columnspacing=1.2
)

plt.savefig("plot/tsptw_jfpc.pdf", bbox_inches="tight", pad_inches=0.01)
plt.show()

