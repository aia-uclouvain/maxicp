import os
import pandas as pd

#filename = "../results/pctsp/pctsp-2025-11-08_11-49-40-2de52fff6"
filename = "../results/pctsp/pctsp-2025-11-08_18-45-34-df1fde783"
transpose = True  # if true, rows are the methods and columns are the instances

#previous:
"""
\begin{tabular}{rrr}
\toprule
Method & v10 & v20\\
\midrule
ORIGINAL & 344 & 136 134\\
\cmidrule(rl){1-3}
MATCHING\_SUCCESSOR & 322 & 101 220\\
MEAN\_INPUT\_AND\_OUTPUT\_SUM & 344 & 136 028\\
MIN\_DETOUR & \textbf{292} & 113 862\\
MIN\_INPUT\_SUM & 342 & 135 688\\
MST & 340 & 135 444\\
MST\_DETOUR & 334 & 132 224\\
SCHEDULING & 310 & \textbf{99 172}\\
\cmidrule(rl){1-3}
ALL & 262 & 61 392\\
\bottomrule
\end{tabular}
"""

df = pd.read_csv(filename, engine="python", sep=" \\| ")
df["instance"] = df["instance"].apply(lambda name: os.path.basename(name).split(".")[0])
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

for instance in completed:
    line = [instance]
    best_sol = None
    best_sol_method = None
    perfs = []
    min_perf = 999_999_999_999
    for method in sorted_methods:
        row = df[((df["instance"] == instance) & (df["variant"] == method))]
        is_completed = row["is_completed"].iloc[0]
        value = row["n_nodes"].iloc[0]
        objective = row["best_obj"].iloc[0]
        if is_completed:
            if best_sol is None:
                best_sol = objective
                best_sol_method = method
            else:
                if best_sol != objective:
                    raise Exception(f"best solution on {instance} differ when proven optimal: {best_sol_method} found {best_sol} but {method} found {objective}")
            perf = str(value)
            if value < int(min_perf) and method != "ALL":
                min_perf = perf
        else:
            perf = " / "
        perfs.append(perf)
    # add bold if best number + add space for the thousand separators
    formatted_perfs = []
    for perf in perfs:
        if perf == " / ":
            formatted = perf
        else:
            formatted = "{:,}".format(int(perf)).replace(",", " ")
            if perf == min_perf:
                formatted = f"\\textbf{{{formatted}}}"
        formatted_perfs.append(formatted)
    line.extend(formatted_perfs)
    content.append(line)

if transpose:
    n_rows = len(content)
    n_cols = len(content[0])
    transposed = [[content[row][col] for row in range(n_rows)] for col in range(n_cols)]
    content = transposed
    content[0][0] = "Method"
else:
    content[0][0] = "Instance"

n_cols = len(content[0])
n_rows = len(content)
header = "\\begin{tabular}{" + ("r" * n_cols) + "}"
print(header)
print("\\toprule")
for i, line in enumerate(content):
    print(" & ".join([entry.replace("_", "\\_") for entry in line]) + "\\\\")
    if i == 0:
        print("\\midrule")
    if has_original and i == 1 and transpose:
        print(f"\\cmidrule(rl){{1-{len(line)}}}")
    if has_all and i == n_rows - 2 and transpose:
        print(f"\\cmidrule(rl){{1-{len(line)}}}")
print("\\bottomrule")
print("\\end{tabular}")
