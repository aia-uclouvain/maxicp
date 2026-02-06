#!/bin/bash
# export JAVA_HOME=$(/usr/libexec/java_home -v 25)
# export PATH=$JAVA_HOME/bin:$PATH

timeout=900  # timeout in seconds
iter=1   # number of iterations per config, to take randomness into account
# compile the project and run the unit tests
echo "compiling and running tests..."
mvn clean package -q # -Dmaven.test.skip
echo "compilation done and tests performed"
# path of the executable
launch_solver=" java -cp target/maxicp-0.0.1-jar-with-dependencies.jar org.maxicp.cp.examples.raw.distance.TSPTWBench"
currentDate=$(date +%Y-%m-%d_%H-%M-%S);
gitShortHash=$(git rev-parse --short HEAD)
outFileOpt="results/tsptw/tsptw-${currentDate}-${gitShortHash}"
# declare -a distanceType=("ORIGINAL" "MIN_INPUT_SUM" "MEAN_INPUT_AND_OUTPUT_SUM" "MIN_DETOUR" "MST" "MATCHING_SUCCESSOR" "MST_DETOUR" "SCHEDULING" "ALL", "FORWARD_SLACK", "SUBSEQUENCE_SPLIT")  # -m, each type of distance constraint to try
declare -a distanceType=("ORIGINAL")  # -m, each type of distance constraint to try
mkdir -p "results/tsptw"  # where the results will be written
rm -f $outFileOpt  # delete filename of the results if it already existed (does not delete past results, unless their datetime is the same)
# the solver must print only one line when it is finished, otherwise we won't get a CSV at the end
# this is the header of the csv. This header needs to change depending on the solver / type of experiment that is being run
# all rows need to be printed by the solver itself
# the id of the commit can be retrieved with the command `git rev-parse HEAD`
echo "class | instance | variant | best_obj | timeout | runtime | n_nodes | n_failures | n_sols | is_completed | solution_list | args " >> $outFileOpt
echo "writing inputs"
# write all the configs into a temporary file
inputFile="inputFileTSPTWOpt"
rm -f $inputFile  # delete previous temporary file if it existed
for (( i=1; i<=$iter; i++ ))  # for each iteration
do
  for distance in "${distanceType[@]}"  # for each distance variant
  do
    # extracts the instances from the data folder
    # write one line per instance containing its filename, along with the distance to use to perform
    find data/TSPTW/RifkiSolnon/ -type f | sed "s/$/,$distance/"  >> $inputFile
  done
done
# at this point, the input file contains rows in the format
# instance_filename,distance_variant
echo "launching experiments in parallel"
# the command line arguments for launching the solver. In this case, the solver is run using
# ./executable -f instance_filename -t timeout -m distance_type -v verbosity
# change this depending on your solver
# the number ({1}, {2}) corresponds to the columns present in the inputFile, beginning at index 1 (i.e. in this case 2 columns, so 1 and 2 are valid columns)
cat $inputFile | parallel -j 8 --colsep ',' $launch_solver -f {1} -m {2} -t $timeout -v 0 >> $outFileOpt
# delete the temporary file
echo "experiments have been run. Results are at ${outFileOpt}"
rm -f $inputFile
