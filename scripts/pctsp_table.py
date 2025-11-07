import os
import pandas as pd

filename = "../results/pctsp/pctsp-2025-11-07_11-27-44-df66e4519"
transpose = False  # if true, rows are the methods and columns are the instances

df = pd.read_csv(filename, engine="python", sep=" \\| ")
df["instance"] = df["instance"].apply(lambda name: os.path.basename(name).split(".")[0])
completed = list(sorted(set(df[df["is_completed"] == True]["instance"])))
methods = set(df["variant"].unique())
sorted_methods = list(sorted(list(methods)))
sorted_methods.remove("ORIGINAL")
sorted_methods = ["ORIGINAL"] + sorted_methods


content = []
# header
line = [" "]
line.extend(sorted_methods)
content.append(line)

for instance in completed:
    line = [instance]
    perfs = []
    min_perf = 999_999_999_999
    for method in sorted_methods:
        value = df[((df["instance"] == instance) & (df["variant"] == method))]
        is_completed = value["is_completed"].iloc[0]
        value = value["n_nodes"].iloc[0]
        if is_completed:
            perf = str(value)
            if value < int(min_perf):
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

n_rows = len(content[0])
header = "\\begin{tabular}{" + ("r" * n_rows) + "}"
print(header)
print("\\toprule")
for i, line in enumerate(content):
    print(" & ".join([entry.replace("_", "\\_") for entry in line]) + "\\\\")
    if i == 0:
        print("\\midrule")
    if i == 1 and transpose:
        print(f"\\cmidrule(rl){{1-{len(line)}}}")
print("\\bottomrule")
print("\\end{tabular}")
