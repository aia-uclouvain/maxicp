import math
import re

import pandas as pd
import os
import matplotlib.pyplot as plt
import numpy as np
from collections import Counter
from matplotlib.lines import Line2D
from matplotlib.ticker import MultipleLocator, PercentFormatter, LogLocator

# list of files and methods to extract for the plots
compare_with_minizinc = False

if compare_with_minizinc:
    filenames = [
        ("results/tmsp/tmsp-2026-03-11_15-41-25-b23bc5aa8",
         {
             "ORIGINAL",
         }
         ),
        ("results/tmsp/tmsp-2026-03-12_14-13-50-ead7e2b62",
         {
             "MIN_DETOUR",
         }
         ),
        ("results/tmsp/tmsp-2026-03-12_16-32-40-36f78ae3b",
         {
             "MIN_INPUT_AND_OUTPUT_SUM",
             "MATCHING_SUCCESSOR"
         }
        ),
        ("results/tmsp-minizinc/tmsp-minizinc-2026-02-26_16-52-08-86dd62902",
         {
             "gecode",
             "cp-sat",
             "chuffed",
             "coin-bc",
             "cplex",
             #"highs",
         }
         ),
    ]
    ignore_files = {"results/tmsp/tmsp-2026-03-12_16-32-40-36f78ae3b"}
    suffix = "_minizinc"
else:
    filenames = [
        ("results/tmsp/tmsp-2026-03-11_15-41-25-b23bc5aa8",
         {
             "ORIGINAL",
         }
         ),
        ("results/tmsp/tmsp-2026-03-12_14-13-50-ead7e2b62",
         {
             "MIN_DETOUR",
         }
         ),
        ("results/tmsp/tmsp-2026-03-12_16-32-40-36f78ae3b",
         {
             "MIN_INPUT_AND_OUTPUT_SUM",
             "MATCHING_SUCCESSOR"
         }
        ),
        ("results/tmsp-minizinc/tmsp-minizinc-2026-02-26_16-52-08-86dd62902",
         {
             "gecode",
             "cp-sat",
             "chuffed",
             "coin-bc",
             "cplex",
             # "highs",
         }
         ),
    ]
    # read to retrieve the best gap, but not shown on the plots
    ignore_files = {"results/tmsp-minizinc/tmsp-minizinc-2026-02-26_16-52-08-86dd62902"}
    suffix = ""

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

markersize = 20
alpha = 0.45
rel_tolerance_similar = 10  # how close in percentage values should be to be considered as similar
rel_tolerance_np = rel_tolerance_similar / 100.0
similar_key = f"Similar ({rel_tolerance_similar}\%)"

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
    "MIN_DETOUR": ORANGE,
    "MATCHING_SUCCESSOR": BLUE,
    "MATCHING_SUCCESSOR2": VERMILION,

    "Improve": SKY_BLUE,
    "Deteriorate": VERMILION,
    similar_key: BLACK,

    "gecode": VERMILION,
    "cp-sat": BLUISH_GREEN,
    "chuffed": BLUE,
    "coin-bc": REDDISH_PURPLE,
    "cplex": SKY_BLUE,
    "highs": YELLOW,
}
naming = {
    "ORIGINAL": "Original",
    "MIN_INPUT_AND_OUTPUT_SUM": "I/O Min",
    "MIN_DETOUR": "Min Detours",
    "MATCHING_SUCCESSOR": "Matching",
    "MATCHING_SUCCESSOR2": "Matching (new)",
    "gecode": "Gecode",
    "cp-sat": "OR-Tools",
    "chuffed": "Chuffed",
    "coin-bc": "Coin-Bc",
    "cplex": "CPLEX",
    "highs": "HiGHS",
}
linestyles = {
    "ORIGINAL": "-",
    "MIN_INPUT_AND_OUTPUT_SUM": "-",
    "MIN_DETOUR": "-",
    "MATCHING_SUCCESSOR": "-",
    "MATCHING_SUCCESSOR2": "-",
    "gecode": "--",
    "cp-sat": "--",
    "chuffed": "--",
    "coin-bc": "--",
    "cplex": "--",
    "highs": "--",
}

global_df = None
all_methods = []
all_best_sols = {}  # best gap per instance
optimal_sol = {}  # optimal solution per instance
instances_unsat = set()
feasible_instances = set()
all_instances = set()
for filename, methods in filenames:
    df = pd.read_csv(filename, engine="python", sep=" \\| ")
    df["instance"] = df["instance"].apply(lambda name: os.path.splitext(os.path.basename(name))[0])
    # keep only the rows with the asked methods
    if "variant" in df.columns:
        df = df[df["variant"].isin(methods)]
        if compare_with_minizinc:
            df = df[["instance", "variant", "best_obj", "timeout", "runtime", "is_completed", "solution_list"]]
        else:
            df = df[["instance", "variant", "best_obj", "timeout", "n_nodes", "runtime", "is_completed", "solution_list"]]
    else:
        # minizinc dataframe
        df["variant"] = df["solver"]
        df = df[df["variant"].isin(methods)]
        df = df[["instance", "variant", "best_obj", "timeout", "runtime", "is_completed", "solution_list"]]
    for i, row in df.iterrows():
        instance = row["instance"]
        best_sol = row["best_obj"]
        if not np.isnan(best_sol):
            # sat instance
            if instance not in all_best_sols:
                all_best_sols[instance] = best_sol
            else:
                all_best_sols[instance] = max(best_sol, all_best_sols[instance])
            if instance in instances_unsat:
                raise ValueError(f"{row['variant']} found a solution for {instance} while another solver claimed that "
                                 f"it was UNSAT.")
            feasible_instances.add(instance)
        if np.isnan(best_sol) and row["is_completed"]:
            # unsat instance
            if instance in feasible_instances:
                raise ValueError(f"{row['variant']} claimed that {instance} is UNSAT while another solver found a "
                                 f"solution for it.")
            instances_unsat.add(instance)
        if row["is_completed"] and not np.isnan(best_sol):
            # checks if the solution is not better than another one previously found
            if instance not in optimal_sol:
                optimal_sol[instance] = best_sol
            if best_sol != optimal_sol[instance]:
                #print(f"{instance}, {optimal_sol[instance]}")
                raise ValueError(f"A solution of cost {best_sol} was proven optimal for instance {instance} with {row['variant']}"
                                 f"while another solver proved a solution of cost {optimal_sol[instance]}.")
        all_instances.add(instance)
    if filename not in ignore_files:
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

def primal_gap(instance: str, sol: float) -> float:
    best_sol = all_best_sols[instance]
    if np.isnan(sol):
        return 100.0
    if sol == best_sol:
        return 0.0
    if sol > best_sol:
        raise ValueError("The value of the best solution does not seem to have been correctly tracked")
    else:
        gap = abs(best_sol - sol) / max(abs(best_sol), abs(sol)) * 100.0
        return gap

# ========== cactus plot ==========

# cactus plot: number of solved instances over time
fig = plt.figure(constrained_layout=True)
gs = fig.add_gridspec(1, 3, width_ratios=[1.0, 0.8, 0.2], wspace=0.05)

ax_left = fig.add_subplot(gs[0, 0])
ax_right = fig.add_subplot(gs[0, 1], sharey=ax_left)
ax_legend = fig.add_subplot(gs[0, 2])

ax_legend.axis("off")

ax_left.yaxis.set_major_locator(MultipleLocator(25))  # 0,25,50,75,100 (within your limits)

for ax in [ax_left, ax_right]:
    ax.grid(axis='y', linestyle='-', alpha=0.7)
    ax.spines['top'].set_visible(False)
    ax.spines['right'].set_visible(False)
    ax.spines['left'].set_visible(False)
    ax.tick_params(axis='y', length=0)

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
    print(f"cactus plot: [{naming[method]}] {n_solved[-1]}% instances solved")
    ax_left.step(t_solved, n_solved, where='post', color=colors[method], label=naming[method], linestyle=linestyles[method])
    # instances that were not solved
    df_unsolved = df_method[df_method["is_completed"] == False]
    # plot the best gap found at the end of the solving process
    counter_list = []
    for i, row in df_unsolved.iterrows():
        instance = row["instance"]
        sol = row["best_obj"]
        gap = primal_gap(instance, sol)
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
    if len(gap_remaining) == 1:
        # only happens if all remaining instances to solve have the same gap
        if gap_remaining[0] != 0.0:
            raise ValueError(f"There seems to be an error for computing the remaining gap for method {method}")
        gap_remaining = [0.0, 100.0]
        n_remaining = [100.0, 100.0]
    ax_right.step(gap_remaining, n_remaining, where='post', color=colors[method], label=naming[method], linestyle=linestyles[method])
ax_left.set_title("Instances solved")
# Legend only for the first subplot, outside the axis, one row
handles, labels = ax_left.get_legend_handles_labels()
ncol = len(labels) if not compare_with_minizinc else 4

# sort the legend by integral value
def area_under_line(line):
    x = np.asarray(line.get_xdata())
    y = np.asarray(line.get_ydata())

    # optional: ignore NaNs / masked gaps
    m = np.isfinite(x) & np.isfinite(y)
    x, y = x[m], y[m]

    # if x isn't sorted, sort it so integration makes sense
    if x.size and np.any(np.diff(x) < 0):
        idx = np.argsort(x)
        x, y = x[idx], y[idx]

    return np.trapezoid(y, x)

areas = [area_under_line(h) for h in handles]

# largest integral first
order = np.argsort(areas)[::-1]
handles_sorted = [handles[i] for i in order]
labels_sorted  = [labels[i]  for i in order]

ax_legend.legend(
    handles_sorted,
    labels_sorted,
    loc="center right",
    ncol=1,
    frameon=False,
    handlelength=1.5,
    borderaxespad=0.0
)

delta_axis = 2
ax_left.set_ylabel("Number of instances [\%]")
ax_left.set_xlabel("Runtime [s]")
ax_left.set_xscale("log")
ax_left.set_ylim([0 - delta_axis, 100 + delta_axis])
#ax_left.yaxis.set_major_formatter(PercentFormatter())

ax_right.set_title("Gap on unsolved instances")
ax_right.set_xlim([0 - delta_axis, 100 + delta_axis])
ax_right.set_xticks([0, 25, 50, 75, 100])
#ax_right.xaxis.set_major_formatter(PercentFormatter())
#axs[1].set_yticks([0, 25, 50, 75, 100])
ax_right.set_xlabel("Gap to b.k.s. [\%]")
# Hide y ticks/labels on the right subplot (keep only left)

ax_right.tick_params(axis='y', left=False, labelleft=False)
ax_right.spines['left'].set_visible(False)

fig.set_figheight(cm_to_inch(5))
fig.set_figwidth(cm_to_inch(14))

figname = f"plot/tmsp_cactus_plot{suffix}.pdf"
plt.savefig(figname, bbox_inches="tight")
print(f"figure saved to {figname}")

# ========== gap over time ==========

# cactus plot: percentage of gap over time
pattern_sol_seqvar = "t=(\d+\.\d+); nodes=(\d+); fails=(\d+); obj=(\d+\.\d+)"
pattern_sol_minizinc = "t=(\d+\.\d+); obj=(\d+\.*\d*)"
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
        if sol_list is not None:
            for sol_tuple_group in re.finditer(pattern_sol_seqvar, sol_list):
                # seqvar solution format
                time = float(sol_tuple_group.group(1))
                node = int(sol_tuple_group.group(2))
                sol = float(sol_tuple_group.group(4))
                gap = primal_gap(instance, sol)
                if len(gaps) > 0 and gap > gaps[-1]:
                    raise ValueError("The gap should only decrease over time")
                times.append(time)
                gaps.append(gap)
            else:  # minizinc solution format
                for sol_tuple_group in re.finditer(pattern_sol_minizinc, sol_list):
                    time = float(sol_tuple_group.group(1))
                    sol = float(sol_tuple_group.group(2))
                    gap = primal_gap(instance, sol)
                    if len(gaps) > 0 and gap > gaps[-1]:
                        raise ValueError("The gap should only decrease over time")
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
    for average_gap_i, average_gap_j in zip(average_gap, average_gap[1:]):
        if average_gap_j > average_gap_i:
            raise ValueError("The gap should only decrease over time")
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
figname = f"plot/tmsp_gap_over_time{suffix}.pdf"
plt.savefig(figname)
print(f"figure saved to {figname}")

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
            if not compare_with_minizinc:
                nodes = df_method[df_method["instance"] == instance]["n_nodes"].iloc[0]
        else:
            runtime = float("inf")
            nodes = float("inf")
        runtime_list.append(runtime)
        if not compare_with_minizinc:
            nodes_list.append(nodes)
    runtime_per_method[method] = runtime_list
    search_nodes_per_method[method] = nodes_list

# 1 to 1 comparison for the runtime
fig, axs = plt.subplots(n_methods, n_methods, figsize=(cm_to_inch(14), cm_to_inch(14)),
                        sharex=True,
                        sharey=True,
                        constrained_layout=True)

for i in range(n_methods):
    for j in range(n_methods):
        method_i = sorted_methods[i]
        method_j = sorted_methods[j]
        list_for_i_when_i_better = []
        list_for_j_when_i_better = []

        list_for_i_when_j_better = []
        list_for_j_when_j_better = []

        list_for_i_when_equal = []
        list_for_j_when_equal = []
        for v_i, v_j in zip(runtime_per_method[method_i], runtime_per_method[method_j]):
            if np.isinf(v_i) or np.isinf(v_j):
                continue
            if v_i == v_j or np.allclose(v_i, v_j, rtol=rel_tolerance_np) or np.allclose(v_j, v_i, rtol=rel_tolerance_np):
                list_for_i_when_equal.append(v_i)
                list_for_j_when_equal.append(v_j)
            elif v_i < v_j:
                list_for_i_when_i_better.append(v_i)
                list_for_j_when_i_better.append(v_j)
            else:
                assert v_i > v_j
                list_for_i_when_j_better.append(v_i)
                list_for_j_when_j_better.append(v_j)
        # diagonal line
        # axs[(i, j)].plot([0.0, 900.0], [0.0, 900.0], '--', alpha=0.6, color="gray", linewidth=1.5)
        # actual values
        axs[(i, j)].scatter(list_for_j_when_i_better, list_for_i_when_i_better, color=colors["Improve"], alpha=alpha,
                            s=markersize, zorder=3)
        axs[(i, j)].scatter(list_for_j_when_j_better, list_for_i_when_j_better, color=colors["Deteriorate"], alpha=alpha,
                            s=markersize, zorder=3)
        axs[(i, j)].scatter(list_for_j_when_equal, list_for_i_when_equal, color=colors[similar_key], alpha=alpha, s=markersize, zorder=3)

        if i == n_methods - 1:
            axs[(i, j)].set_xlabel(naming[method_j])
        if j == 0:
            axs[(i, j)].set_ylabel(naming[method_i])

xmin_overal, xmax_overal = 0.05, 1500
for ax in axs.flat:
    ax.set_xscale("log")
    ax.set_yscale("log")
    ax.set_box_aspect(1)

    xmin, xmax = ax.get_xlim()
    ymin, ymax = ax.get_ylim()
    #xmin_overal = min(xmin_overal, xmin, ymin)
    #xmax_overal = max(xmax_overal, xmax, ymax)

    # ax.grid(True, zorder=0)
    ax.grid(which="major", linewidth=1, zorder=0, alpha=0.6)
    # ax.grid(which="minor", linewidth=0.3, alpha=0.3, zorder=0)

    ax.spines['top'].set_visible(False)
    ax.spines['right'].set_visible(False)
    ax.spines['left'].set_visible(False)
    ax.spines['bottom'].set_visible(False)

    ax.xaxis.set_major_locator(LogLocator(base=10, numticks=5))
    ax.yaxis.set_major_locator(LogLocator(base=10, numticks=5))

    #ax.set_xticks([1e3, 1e4, 1e5, 1e6, 1e7])
    #ax.set_yticks([1e3, 1e4, 1e5, 1e6, 1e7])

    ax.label_outer()
    ax.tick_params(axis='y', length=0)
    ax.tick_params(axis='x', length=0)
for ax in axs.flat:
    # diagonal line
    ax.plot([xmin_overal, xmax_overal], [xmin_overal, xmax_overal], alpha=0.6, color="gray", linewidth=1.5, zorder=0, linestyle="dashed")

axs[0, 0].set_xlim(xmin_overal, xmax_overal)
axs[0, 0].set_ylim(xmin_overal, xmax_overal)


fig.suptitle("Pairwise runtime comparison")

legend_elements = [
    Line2D([0], [0], marker='o', alpha=alpha, linestyle='None', color=colors["Improve"], label="Improve"),
    Line2D([0], [0], marker='o', alpha=alpha, linestyle='None', color=colors["Deteriorate"], label="Deteriorate"),
    Line2D([0], [0], marker='o', alpha=alpha, linestyle='None', color=colors[similar_key], label=similar_key),
    Line2D([0, 1], [0, 0], alpha=0.6, color="gray", linewidth=1.5, linestyle="dashed", label="Identical"),
]

fig.legend(
    handles=legend_elements,
    loc="upper center",
    bbox_to_anchor=(0.5, -0.01),  # centered below the figure
    ncol=4,  # one row
    frameon=False,
    handletextpad=0.6,
    columnspacing=1.2
)

figname = f"plot/tmsp_runtime_comparison{suffix}.pdf"
plt.savefig(figname, bbox_inches="tight", pad_inches=0.01)
print(f"figure saved to {figname}")

if not compare_with_minizinc:
    # 1 to 1 comparison for the number of nodes
    fig, axs = plt.subplots(n_methods, n_methods, figsize=(cm_to_inch(14), cm_to_inch(14)),
    sharex=True,
    sharey=True,
                            constrained_layout=True)
    for i in range(n_methods):
        for j in range(n_methods):
            method_i = sorted_methods[i]
            method_j = sorted_methods[j]
            list_for_i_when_i_better = []
            list_for_j_when_i_better = []

            list_for_i_when_j_better = []
            list_for_j_when_j_better = []

            list_for_i_when_equal = []
            list_for_j_when_equal = []
            max_val = 0
            for v_i, v_j in zip(search_nodes_per_method[method_i], search_nodes_per_method[method_j]):
                if np.isinf(v_i) or np.isinf(v_j):
                    continue
                max_val = max(max_val, v_i, v_j)
                if v_i == v_j or np.allclose(v_i, v_j, rtol=rel_tolerance_np) or np.allclose(v_j, v_i, rtol=rel_tolerance_np):
                    list_for_i_when_equal.append(v_i)
                    list_for_j_when_equal.append(v_j)
                elif v_i < v_j:
                    list_for_i_when_i_better.append(v_i)
                    list_for_j_when_i_better.append(v_j)
                else:
                    assert v_i > v_j
                    list_for_i_when_j_better.append(v_i)
                    list_for_j_when_j_better.append(v_j)
            # actual values

            axs[(i, j)].scatter(list_for_j_when_i_better, list_for_i_when_i_better, color=colors["Improve"], alpha=alpha,
                                s=markersize, zorder=3)
            axs[(i, j)].scatter(list_for_j_when_j_better, list_for_i_when_j_better, color=colors["Deteriorate"], alpha=alpha,
                                s=markersize, zorder=3)
            axs[(i, j)].scatter(list_for_j_when_equal, list_for_i_when_equal, color=colors[similar_key], alpha=alpha, s=markersize, zorder=3)

            if i == n_methods - 1:
                axs[(i, j)].set_xlabel(naming[method_j])
            if j == 0:
                axs[(i, j)].set_ylabel(naming[method_i])

    #for ax in axs.ravel():
    #    ax.xaxis.labelpad = 14   # increase gap label <-> ticks/offset

    xmin_overal, xmax_overal = float("inf"), float("-inf")
    for ax in axs.flat:
        ax.set_xscale("log")
        ax.set_yscale("log")
        ax.set_box_aspect(1)

        xmin, xmax = ax.get_xlim()
        ymin, ymax = ax.get_ylim()
        xmin_overal = min(xmin_overal, xmin, ymin)
        xmax_overal = max(xmax_overal, xmax, ymax)

        # ax.grid(True, zorder=0)
        ax.grid(which="major", linewidth=1, zorder=0, alpha=0.6)
        # ax.grid(which="minor", linewidth=0.3, alpha=0.3, zorder=0)

        ax.spines['top'].set_visible(False)
        ax.spines['right'].set_visible(False)
        ax.spines['left'].set_visible(False)
        ax.spines['bottom'].set_visible(False)

        ax.xaxis.set_major_locator(LogLocator(base=10, numticks=5))
        ax.yaxis.set_major_locator(LogLocator(base=10, numticks=5))

        #ax.set_xticks([1e3, 1e4, 1e5, 1e6, 1e7])
        #ax.set_yticks([1e3, 1e4, 1e5, 1e6, 1e7])

        ax.label_outer()
        ax.tick_params(axis='y', length=0)
        ax.tick_params(axis='x', length=0)
    for ax in axs.flat:
        # diagonal line
        ax.plot([xmin_overal, xmax_overal], [xmin_overal, xmax_overal], alpha=0.6, color="gray", linewidth=1.5, zorder=0, linestyle="dashed")

    axs[0, 0].set_xlim(xmin_overal, xmax_overal)
    axs[0, 0].set_ylim(xmin_overal, xmax_overal)

    fig.suptitle("Pairwise comparison of search tree nodes")

    legend_elements = [
        Line2D([0], [0], marker='o', linestyle='None', alpha=alpha, color=colors["Improve"], label="Improve"),
        Line2D([0], [0], marker='o', linestyle='None', alpha=alpha, color=colors["Deteriorate"], label="Deteriorate"),
        Line2D([0], [0], marker='o', linestyle='None', alpha=alpha, color=colors[similar_key], label=similar_key),
        Line2D([0, 1], [0, 0], alpha=0.6, color="gray", linewidth=1.5, linestyle="dashed", label="Identical"),
    ]

    fig.legend(
        handles=legend_elements,
        loc="upper center",
        bbox_to_anchor=(0.5, -0.01),   # centered below the figure
        ncol=4,                       # one row
        frameon=False,
        handletextpad=0.6,
        columnspacing=1.2
    )

    figname = f"plot/tmsp_nodes_comparison{suffix}.pdf"
    plt.savefig(figname, bbox_inches="tight", pad_inches=0.01)
    print(f"figure saved to {figname}")
