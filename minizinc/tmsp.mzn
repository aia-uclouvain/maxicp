include "globals.mzn";

% input data

int: n;  % number of nodes within the problem
int: start;  % start vertex
int: end; % end vertex
int: H;  % maximum distance allowed
set of int: allNodes = 1..n;  % all nodes within the problem
set of int: mandatory;  % set of nodes marked as mandatory
array[allNodes, allNodes] of int: d; % distance matrix 
array[allNodes] of int: score;  % score of every node

% ----- variables -----

% successor of a node
array[allNodes] of var allNodes: succ;
% distance to the successor of a node
array[allNodes] of var int: distSucc;
% reward of a node
array[allNodes] of var int: reward;
% total reward
var int: totReward;

% ----- constraints -----

% the successor of the end is always the start node
constraint succ[end] = start;
% start node cannot make a self loop
constraint succ[start] != start; 
% the nodes must be connected in a subcircuit:
% they are either connected to 1 circuit or disconnected and form a self-loop
constraint subcircuit(succ);

% mandatory nodes must be connected to the circuit ; they cannot form a self-loop
constraint forall(node in mandatory)
  (succ[node] != node);
  
% tracks the distance to a successor
constraint
  forall(node in allNodes where node != end) (
    distSucc[node] = d[node, succ[node]]
  );

constraint
  distSucc[end] = 0;
  
% collected reward at a node
constraint forall(node in allNodes)
  (reward[node] = score[node] * (succ[node] != node));

% total distance is limited
constraint (sum(distSucc) <= H);

% tracks the total reward
constraint (totReward = sum(reward));

% maximize the total reward
solve maximize(totReward);
output [show(totReward)];


  