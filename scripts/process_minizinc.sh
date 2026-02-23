#!/usr/bin/env bash

################################################################################
# USAGE:
#   ./capture_objectives.sh <solver> <model> <instance> <timeLimit>
#
# EXAMPLE:
#   ./capture_objectives.sh my_model.mzn 5000 R1b.dzn
#
# WHAT IT DOES:
#   1) Reads four arguments: solver, model, instance, and timeLimit
#   2) Constructs the MiniZinc command:
#        minizinc --time-limit <timeLimit> --solver <solver> <model> <instance> --intermediate
#   3) Captures the solver's output line-by-line.
#   4) Whenever it sees a line that matches a floating-point number, it captures
#      that value plus the elapsed time (rounded to 3 decimals).
#   5) Finally, prints a list of tuples "(objective, timestamp)".
################################################################################

# Check that we have exactly 5 arguments
if [[ $# -ne 5 ]]; then
    echo "Usage: $0 <solver> <model> <timeLimit> <instance> <seed>"
    exit 1
fi

# Read positional arguments
solver="$1"
model="$2"
instance="$3"
timeLimitSec="$4"   # The user-provided time limit *in seconds* (can be a decimal)
seed="$5"

# Convert seconds -> milliseconds (as an integer).
# For example, if timeLimitSec="5", then timeLimitMs=5000
# If timeLimitSec="3.2", then timeLimitMs=3200, etc.
timeLimitMs=$(awk -v s="$timeLimitSec" 'BEGIN { printf "%d", s * 1000 }')

# Construct the command array
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

# We'll accumulate results in an array
results=()

# Run the command and read its output line-by-line
while IFS= read -r line; do

    # Regex to check if line is a valid float (with optional sign, decimal, exponent)
    if [[ "$line" =~ ^[+-]?[0-9]*(\.[0-9]+)?([eE][+-]?[0-9]+)?$ ]]; then

        # Current time
        now=$(date +%s.%N)

        # Calculate elapsed time (float)
        elapsed=$(echo "$now - $start_time" | bc -l)

        # Round to 3 decimal places, add leading zero if needed (e.g. 0.223)
        rounded_elapsed=$(printf "%.3f" "$elapsed")
        bestObj=$(printf "%.3f" "$line")

        # Store as "(objective, timestamp)"
        results+=("($line, $rounded_elapsed)")
    fi
done < <("${cmd[@]}")


instanceShortened=$(basename "$instance")
modelShortened=$(basename "$model")
timeLimitSecFormatted=$(printf "%.3f" "$timeLimitSec")
# collected tuples are space-separated
echo "$solver | $modelShortened | $instanceShortened | $bestObj | $timeLimitSecFormatted | $seed | ${results[@]}"
