# branch_and_price-cvrp

A simple implementation of branch and price algorithm(B&P) on a classical capacitated vehicle routing problem with single depot and some customers, I follow a breadth first search principle to explore new nodes. The master problem is formulated as a set-partitioning problem which is solved using commercial solver Gurobi, while sub problem is solved using a 2-cycle elimination labeling algorithm.

The instance is randomly generated, the file name has the format "vrp_bp_#", while "#" denotes the number of customer. The demand of each customer is set as a random integer between [30,100], while cost is between [5,80], and the capacity is set to 300.

"Framework.java" is program entry, the instance of class "BnbNode" denotes a node of branching tree, "UserParam" is used to records the parameters of model and algorithm, as well as the global upper_bound.
