#!/usr/bin/env bash

################################################################################
# USAGE:
#   ./capture_objectives.sh <solver> <model> <instance> <timeLimit> <seed>
#
# OUTPUT FORMAT:
#   solver | model | instance | best_obj | timeout | runtime | is_completed | seed | solutions_over_time
#
# COMPLETION RULES:
#   - If minizinc outputs "==========", then search completed (optimal proven).
#   - If minizinc outputs "=====UNSATISFIABLE=====", then search completed (UNSAT proven).
#   - Otherwise, search not completed.
################################################################################

# Check that we have exactly 5 arguments
if [[ $# -ne 5 ]]; then
    echo "Usage: $0 <solver> <model> <instance> <timeLimit> <seed>"
    exit 1
fi

# Read positional arguments
solver="$1"
model="$2"
instance="$3"
timeLimitSec="$4"   # seconds (can be decimal)
seed="$5"

# Convert seconds -> milliseconds (integer)
timeLimitMs=$(awk -v s="$timeLimitSec" 'BEGIN { printf "%d", s * 1000 }')

# Construct the command array
cmd=(
  minizinc
  --time-limit "$timeLimitMs"
  --solver "$solver"
  "$model"
  "$instance"
  --intermediate
  -r "$seed"
)

# Record the start time
start_time=$(date +%s.%N)

bestObj="NaN"
is_completed="false"

# We'll accumulate results in an array
results=()

# Run the command and read its output line-by-line
while IFS= read -r line; do
    # Detect completion markers
    if [[ "$line" == "==========" ]] || [[ "$line" == "=====UNSATISFIABLE=====" ]]; then
        is_completed="true"
        continue
    fi

    # Regex to check if line is a valid float (with optional sign, decimal, exponent)
    if [[ "$line" =~ ^[+-]?[0-9]*(\.[0-9]+)?([eE][+-]?[0-9]+)?$ ]]; then
        now=$(date +%s.%N)
        elapsed=$(echo "$now - $start_time" | bc -l)
        rounded_elapsed=$(printf "%.3f" "$elapsed")

        bestObj=$(printf "%.3f" "$line")
        results+=("(t=$rounded_elapsed; obj=$line)")
    fi
done < <("${cmd[@]}")

# Final runtime
now=$(date +%s.%N)
elapsed=$(echo "$now - $start_time" | bc -l)
runTime=$(printf "%.3f" "$elapsed")

instanceShortened=$(basename "$instance")
modelShortened=$(basename "$model")
timeLimitSecFormatted=$(printf "%.3f" "$timeLimitSec")

# collected tuples are space-separated
echo "$solver | $modelShortened | $instanceShortened | $bestObj | $timeLimitSecFormatted | $runTime | $is_completed | $seed | ${results[@]}"