import os
import re
from copy import deepcopy

import pandas as pd

# rows are methods and columns are the instances
# filename = "../results/otsp/otsp-2025-12-04_13-41-10-0aecc08fb"
filename = "../results/otsp/instance_30"

df = pd.read_csv(filename, engine="python", sep=" \\| ")
df["instance"] = df["instance"].apply(lambda name: os.path.basename(name).split(".")[0].replace("instance_", ""))
completed = list(sorted(set(df[df["is_completed"] == True]["instance"])))
methods = set(df["variant"].unique())
has_original = "ORIGINAL" in methods
has_all = "ALL" in methods
sorted_methods = list(sorted(list(methods)))
if has_original:
    sorted_methods.remove("ORIGINAL")
    sorted_methods = ["ORIGINAL"] + sorted_methods
if has_all:
    sorted_methods.remove("ALL")
    sorted_methods = sorted_methods + ["ALL"]


content = []
# header
line = [" "]
line.extend(sorted_methods)
content.append(line)
ranking_for_best = []

for instance_idx, instance in enumerate(completed):
    line = [instance]
    best_sol = None
    best_sol_method = None
    perfs = []
    perfs_as_numbers = []
    min_perf = 999_999_999_999
    for method in sorted_methods:
        row = df[((df["instance"] == instance) & (df["variant"] == method))]
        is_completed = row["is_completed"].iloc[0]
        #value = row["n_nodes"].iloc[0]
        value = row["runtime"].iloc[0]
        objective = row["best_obj"].iloc[0]
        if is_completed:
            if best_sol is None:
                best_sol = objective
                best_sol_method = method
            else:
                if best_sol != objective:
                    raise Exception(f"best solution on {instance} differ when proven optimal: {best_sol_method} found {best_sol} but {method} found {objective}")
            perfs_as_numbers.append(value)
            perf = str(value)
            #if value < int(min_perf) and method != "ALL":
            if value < float(min_perf) and method != "ALL":
                min_perf = perf
        else:
            perf = " / "
            perfs_as_numbers.append(float("inf"))
        perfs.append(perf)
    # add bold if best number + add space for the thousand separators
    formatted_perfs = []
    for perf in perfs:
        if perf == " / ":
            formatted = perf
        else:
            formatted = "{:,}".format(float(perf)).replace(",", " ")
            if perf == min_perf:
                formatted = f"\\textbf{{{formatted}}}"
        formatted_perfs.append(formatted)
    if instance_idx == len(completed) - 1:
        # create the method ranking for the table
        for i, method in enumerate(sorted_methods):
            pass
    line.extend(formatted_perfs)
    content.append(line)

n_rows = len(content)
n_cols = len(content[0])
# ranking of the best methods according to the last instance (except for original and all)
"""
if has_original and has_all:
    ranking = list(range(2, n_cols-1))
elif has_all:
    ranking = list(range(1, n_cols-1))
elif has_original:
    ranking = list(range(2, n_cols))
else:
    ranking = list(range(n_cols))
original_ranking = deepcopy(ranking)
pattern = "(\s|\d)+"
ranking.sort(key=lambda method_idx: int(re.search(pattern, content[-1][method_idx]).group(0).replace(" ", "")), reverse=True)
# apply the ranking to get the best last performing methods first
"""
transposed = [[content[row][col] for row in range(n_rows)] for col in range(n_cols)]
content = transposed
content[0][0] = "Method"
"""
content_copy = deepcopy(content)
for origin_row, destination_row in zip(original_ranking, ranking):
    content_copy[destination_row] = content[origin_row]
content = content_copy
"""

n_cols = len(content[0])
n_rows = len(content)
header = "\\begin{tabular}{" + ("r" * n_cols) + "}"
print(header)
print("\\toprule")
# TODO: change the method ordering based on best performance


for i, line in enumerate(content):
    if i == 0:
        # if all entries are in the form "(\d+)_(\d+)", they correspond to "nNodes_nRequired"
        # split this info over two lines to make a nicer table
        all_entries_can_be_split = True
        pattern = "(\d+)_(\d+)"
        first_line = []
        second_line = []
        for column, entry in enumerate(line):
            if column == 0:
                first_line.append("\\#customers")
                second_line.append("\\#required")
            else:
                if len(entry) > 0:
                    if (search_obj := re.search(pattern, entry)) is not None:
                        all_entries_can_be_split = True
                        instance_name = str(int(search_obj.group(1)))
                        n_required = str(int(search_obj.group(2)))
                        first_line.append(instance_name)
                        second_line.append(n_required)
                    else:
                        all_entries_can_be_split = False
                        break
                else:
                    first_line.append("")
                    second_line.append("")
        if all_entries_can_be_split:
            # for the first line, merge the columns that are in common
            first_line.append("")
            merged = []
            last_entry = first_line[0]
            last_merged_column = 0
            for column, entry in enumerate(first_line):
                if entry != last_entry:
                    merged.append((last_merged_column+1, column))
                    n_merged = column - last_merged_column
                    if len(merged) > 1:
                        print(f" & \\multicolumn{{{n_merged}}}{{c}}{{{last_entry}}}", end="")
                    else:
                        print(last_entry, end="")
                    last_entry = entry
                    last_merged_column = column
            print("\\\\")
            #print(" & ".join([entry.replace("_", "\\_") for entry in first_line]) + "\\\\")
            for start, end in merged[1:]:
                print(f"\\cmidrule(lr){{{start}-{end}}}")
            print(" & ".join([entry.replace("_", "\\_") for entry in second_line]) + "\\\\")
        else:
            print(" & ".join([entry.replace("_", "\\_") for entry in line]) + "\\\\")
        print("\\midrule")
    else:
        print(" & ".join([entry.replace("_", "\\_") for entry in line]) + "\\\\")
        if has_original and i == 1:
            print(f"\\cmidrule(rl){{1-{len(line)}}}")
        if has_all and i == n_rows - 2:
            print(f"\\cmidrule(rl){{1-{len(line)}}}")
print("\\bottomrule")
print("\\end{tabular}")


# example of correct split with thousands separators and bold numbers:
"""
\begin{tabular}{l
		r
		@{\,} r
		@{\,} r}
	\toprule
	Method & \multicolumn{3}{c}{instance} \\
	\midrule
	a       & 4 & 132 & 062 \\
	b       & {} & {\bfseries 411} & {\bfseries 706} \\
	c       & 2 & 045 & 690 \\
	\midrule
	summary & {} & 202 & 428 \\
	\bottomrule
\end{tabular}
"""
