timeoutS=900  # timeout in seconds
iter=1   # number of iterations, to take randomness into account
model="minizinc/tmsp.mzn"

launch_solver="scripts/process_minizinc.sh"

currentDate=$(date +%Y-%m-%d_%H-%M-%S);  #
gitShortHash=$(git rev-parse --short HEAD)
outFileOpt="results/tmsp-minizinc/tmsp-minizinc-${currentDate}-${gitShortHash}"
mkdir -p "results/tmsp-minizinc"  # where the results will be written
rm -f $outFileOpt  # delete filename of the results if it already existed (does not delete past results, unless their datetime is the same)

echo "solver | model | instance | best_obj | timeout | seed | solutions_over_time" >> $outFileOpt

declare -a solvers=("gecode" "cp-sat" "chuffed" "coin-bc" "cplex" "highs")  # -d
inputFile="inputFileTMSP"
rm -f $inputFile  # delete previous temporary file if it existed

echo "writing inputs"
for (( i=1; i<=$iter; i++ ))  # for each iteration
do
  for solver in "${solvers[@]}"  # for each solver
  do
    find data/TMSP_minizinc -type f | sed "s/$/,$solver/"  >> $inputFile
  done
done

# add a comma and a random seed at the end of each line
awk -v seed=$RANDOM '{
    srand(seed + NR); # seed the random number generator with a combination of $RANDOM and the current record number
    rand_num = int(rand() * 10000); # generate a random number. Adjust the multiplier for the desired range.
    print $0 "," rand_num;
}' $inputFile | sponge $inputFile
echo "launching experiments in parallel"
cat $inputFile | parallel --colsep ',' $launch_solver {2} $model {1} $timeoutS {3} >> $outFileOpt
echo "experiments have been run"
rm -f $inputFile