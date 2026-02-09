import re

import pandas as pd
import os
import matplotlib.pyplot as plt
import numpy as np
from collections import Counter

# list of files and methods to extract for the plots
filenames = [
    ("results/pctsptw/pctsptw-2026-02-07_21-16-26-8ab0e44b6", {"MIN_INPUT_AND_OUTPUT_SUM", "MIN_DETOUR", "MST_DETOUR"}),
    ("results/pctsptw/pctsptw-2026-02-03_23-37-23-f92b69cde", {"ORIGINAL"}),
]

plt.rcParams.update({
    # use actual LaTeX via text.usetex
    'text.usetex': True,

    # match LIPIcs font families
    'font.family': 'serif',
    'font.serif': ['Latin Modern Roman'],
    'font.sans-serif': ['Latin Modern Sans'],

    # generally aim for ~10pt body text
    'font.size': 10,          # main default text size
    'axes.titlesize': 10,     # axis titles
    'axes.labelsize': 10,     # axis labels
    'xtick.labelsize': 9,     # tick labels
    'ytick.labelsize': 9,     # tick labels
    'legend.fontsize': 9,     # legend text
    'figure.titlesize': 10,   # overall figure title
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
    "MIN_DETOUR": BLUISH_GREEN,
    "MST_DETOUR": ORANGE,
}
naming = {
    "ORIGINAL": "Original",
    "MIN_INPUT_AND_OUTPUT_SUM": "I/O Min",
    "MIN_DETOUR": "Min Detours",
    "MST_DETOUR": "Restricted detours",
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

# ========== cactus plot ==========

# cactus plot: number of solved instances over time
fig, axs = plt.subplots(1,2)
for method in all_methods:
    df_method = global_df[global_df["variant"] == method]
    # remove the instances that were proven to be UNSAT
    #df_method = df_method[~((df_method["is_completed"] == True) & (df_method["best_obj"].isna()))]
    df_method = df_method[~df_method["instance"].isin(instances_unsat)]
    n_instances = df_method["instance"].nunique()
    # extract the time at which the instances were solved
    df_solved = df_method[df_method["is_completed"]]["runtime"]
    solved_counter = Counter(list(df_solved))
    t_solved = [0.0]
    n_solved = [0]
    n_solved_tot = 0
    for t, n in sorted(solved_counter.items(), key=lambda x: x[0]):
        n_solved_tot += n
        t_solved.append(t)
        n_solved.append(n_solved_tot / n_instances * 100.0)
    t_solved.append(df_method["timeout"].iloc[0])
    n_solved.append(n_solved_tot / n_instances * 100.0)
    axs[0].plot(t_solved, n_solved, color=colors[method], label=naming[method])
    # instances that were not solved
    df_unsolved = df_method[df_method["is_completed"] == False]
    # plot the best gap found at the end of the solving process
    counter_list = []
    for i, row in df_unsolved.iterrows():
        instance = row["instance"]
        sol = row["best_obj"]
        best_sol = all_best_sols[instance]
        if np.isnan(sol):
            gap = 100.0
        else:
            gap = abs(best_sol - sol) / max(abs(best_sol), abs(sol))
        # gap = round(gap, 3)
        counter_list.append(gap)
    gap_counter = Counter(counter_list)
    if 0.0 not in gap_counter:
        gap_remaining = [0.0]
        n_remaining = [n_solved_tot / n_instances * 100.0]
    else:
        gap_remaining = []
        n_remaining = []
    for gap, n in sorted(gap_counter.items(), key=lambda x: x[0]):
        n_solved_tot += n
        gap_remaining.append(gap)
        n_remaining.append(n_solved_tot / n_instances * 100.0)
    axs[1].plot(gap_remaining, n_remaining, color=colors[method], label=naming[method])
axs[0].set_title("Instances solved")
# Legend only for the first subplot, outside the axis, one row
handles, labels = axs[0].get_legend_handles_labels()
fig.legend(handles, labels, loc="lower center",
           bbox_to_anchor=(0.5, 0), ncol=len(labels), handlelength=1.0, columnspacing=1.5)
delta_axis = 2
axs[0].set_ylabel("Percentage of instances")
axs[0].set_xlabel("Runtime [s]")
axs[0].set_xscale("log")
axs[1].set_title("Gap on instances")
axs[1].set_xlim([0 - delta_axis, 100 + delta_axis])
axs[1].set_ylim([0 - delta_axis, 100 + delta_axis])
axs[0].set_ylim([0 - delta_axis, 100 + delta_axis])
axs[1].set_xlabel("Gap to b.k.s. [\%]")
fig.set_figheight(cm_to_inch(6))
fig.set_figwidth(cm_to_inch(14))
plt.tight_layout(rect=[0, 0.1, 1, 1])
plt.savefig("plot/cactus_plot.pdf")
plt.show()


# ========== gap over time ==========

# cactus plot: percentage of gap over time
pattern_sol = "t=(\d+\.\d+); nodes=(\d+); fails=(\d+); obj=(\d+\.\d+)"
fig = plt.figure()
for method in all_methods:
    df_method = global_df[global_df["variant"] == method]
    # remove the instances that were proven to be UNSAT
    df_method = df_method[~df_method["instance"].isin(instances_unsat)]

    per_instance_times = []
    per_instance_nodes = []
    per_instance_gaps = []

    for i, row in df_method.iterrows():
        instance = row["instance"]
        best_sol = all_best_sols[instance]
        sol_list = row["solution_list"]
        # values for this instance
        times = [0.0]  # at time 0, no sol found
        gaps = [100.0]
        for sol_tuple_group in re.finditer(pattern_sol, sol_list):
            time = float(sol_tuple_group.group(1))
            node = int(sol_tuple_group.group(2))
            sol = float(sol_tuple_group.group(4))
            if np.isnan(sol):
                gap = 100.0
            else:
                gap = abs(best_sol - sol) / max(abs(best_sol), abs(sol))
            times.append(time)
            gaps.append(gap)

        per_instance_times.append(np.array(times, dtype=float))
        per_instance_gaps.append(np.array(gaps, dtype=float))

    all_times = np.unique(np.concatenate(per_instance_times))
    all_times.sort()

    # Evaluate each instance step function on all_times (forward-fill)
    gaps_matrix = []
    for times_i, gaps_i in zip(per_instance_times, per_instance_gaps):
        idx = np.searchsorted(times_i, all_times, side="right") - 1
        gaps_on_grid = np.where(idx >= 0, gaps_i[idx], 100.0)
        gaps_matrix.append(gaps_on_grid)

    gaps_matrix = np.vstack(gaps_matrix)
    average_gap = gaps_matrix.mean(axis=0)

    # Now plot: x=all_times, y=average_gap
    plt.plot(all_times, average_gap, label=naming[method], color=colors[method])

ax = plt.gca()
ax.legend()
delta_axis = 2
ax.set_ylabel("Gap to b.k.s. [\%]")
ax.set_xlabel("Runtime [s]")
ax.set_ylim([0 - delta_axis, 100 + delta_axis])

fig.set_figheight(cm_to_inch(6))
fig.set_figwidth(cm_to_inch(14))
plt.tight_layout()
plt.savefig("plot/gap_over_time.pdf")
plt.show()

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
fig, axs = plt.subplots(n_methods, n_methods)
for i in range(n_methods):
    for j in range(n_methods):
        method_i = sorted_methods[i]
        method_j = sorted_methods[j]
        list_for_i = []
        list_for_j = []
        for v_i, v_j in zip(runtime_per_method[method_j], runtime_per_method[method_i]):
            if np.isinf(v_i) or np.isinf(v_j):
                continue
            list_for_i.append(v_i)
            list_for_j.append(v_j)
        # diagonal line
        axs[(i, j)].plot([0.0, 900.0], [0.0, 900.0], '--', alpha=0.7, color="gray")
        # actual values
        axs[(i, j)].plot(list_for_i, list_for_j, '.')
        if i == n_methods - 1:
            axs[(i, j)].set_xlabel(naming[method_j])
        if j == 0:
            axs[(i, j)].set_ylabel(naming[method_i])


fig.set_figheight(cm_to_inch(14))
fig.set_figwidth(cm_to_inch(14))
fig.suptitle("Runtime of one method against another\n(only on instances solved by both)")
plt.tight_layout()
plt.savefig("plot/runtime_comparison.pdf")
plt.show()

# 1 to 1 comparison for the number of nodes
fig, axs = plt.subplots(n_methods, n_methods)
for i in range(n_methods):
    for j in range(n_methods):
        method_i = sorted_methods[i]
        method_j = sorted_methods[j]
        list_for_i = []
        list_for_j = []
        for v_i, v_j in zip(search_nodes_per_method[method_j], search_nodes_per_method[method_i]):
            if np.isinf(v_i) or np.isinf(v_j):
                continue
            list_for_i.append(v_i)
            list_for_j.append(v_j)
        # diagonal line
        max_val = max(max(list_for_i), max(list_for_j))
        axs[(i, j)].plot([0, max_val], [0, max_val], '--', alpha=0.7, color="gray")
        # actual values
        axs[(i, j)].plot(list_for_i, list_for_j, '.')
        if i == n_methods - 1:
            axs[(i, j)].set_xlabel(naming[method_j])
        if j == 0:
            axs[(i, j)].set_ylabel(naming[method_i])


fig.set_figheight(cm_to_inch(14))
fig.set_figwidth(cm_to_inch(14))
fig.suptitle("Number of nodes to prove optimality\n(only on instances solved by both)")
plt.tight_layout()
plt.savefig("plot/nodes_comparison.pdf")
plt.show()
