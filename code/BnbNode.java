package BnP_Framework;
import gurobi.*;
import java.util.*;
import java.util.stream.Collectors;
/*
Each instance is a node in the branch tree
'arcs' attribute is the available arc of the node, and 'routes' is the initial routes from the parent node, respectively
'solutionRoutes' is the final routes after optimization, and 'objVal' records the objective value
'fractionArc' records the arc that be fractionally visited, we branch the arc that be visited closest to 0.5 to branch
'feasible' records the status of the current node, if infeasible we can cut it from the branch tree
'depth' records the current depth in the branch tree, while 'parent' and 'child' record its parent and child node
columnGeneration() is used to optimize the relaxed model, while the sub problem is solved by labeling algorithm
 */
public class BnbNode {
    List<List<Integer>> arcs;   //the set of arcs of this node
    List<Route> routes = new ArrayList<Route>(); //current routes
    List<Route> solutionRoutes = new ArrayList<Route>(); //  the routes of final solution of the current node
    Map<List<Integer>, Double> fractionArc = new HashMap<List<Integer>, Double>();   // the sets of fractional arcs
    double objVal;
    double lowerBound;
    BnbNode parent;
    List<BnbNode> child = new ArrayList<BnbNode>();
    boolean feasible = true;   // if the relaxed solution is feasible
    int depth;  // the depth of the node in the branching tree

    // generate root node
    BnbNode(List<List<Integer>> arcs, List<Route> routes){
        this.arcs = arcs;
        this.routes = routes;
        this.parent = null; // root node has no parent
        this.depth = 0; // the depth of the root node is 0
        this.lowerBound = Double.NEGATIVE_INFINITY;
    }
    // generate new node
    BnbNode(List<List<Integer>> arcs, List<Route> routes, BnbNode parent){
        this.arcs = arcs;
        this.routes = routes;
        this.parent = parent;
        this.depth = parent.depth+1;
        this.lowerBound = parent.lowerBound;
    }

    public List<Route> getRoutes(){
        return this.routes;
    }

    @Override
    public String toString() {
        return "BnbNode{" +
                ", objVal=" + objVal +
                ", lowerBound=" + lowerBound +
                "solutionRoutes=" + solutionRoutes +
                ", depth=" + depth +
                '}';
    }

    //  column generation phase, since we have an initial route that only visit each customer like 0->i->0,
    //  the master problem is always feasible
    public void columnGeneration(){
        try{
            int count = 1; // used to record the variable number
            GRBEnv env = new GRBEnv();
            GRBModel mp = new GRBModel(env);
            mp.set(GRB.IntParam.LogToConsole, 0);
            GRBVar[] y = new GRBVar[this.routes.size()];
            for(int i = 0;i<this.routes.size();i++){
                String name = "y"+count;
                count ++;
                y[i] = mp.addVar(0.0, 1.0, this.routes.get(i).cost, GRB.CONTINUOUS, name);
            }
//            add constraints
            GRBLinExpr expr = new GRBLinExpr();
            for(int i=1;i<UserParam.nodeCount;i++){
                expr.clear();
                for(int j=0;j<this.routes.size();j++){
                    if(this.routes.get(j).path.contains(i)){
                        expr.addTerm(1.0, y[j]);
                    }else{
                        expr.addTerm(0.0, y[j]);
                    }
                }
                mp.addConstr(expr, GRB.EQUAL, 1.0, "con");
            }
            mp.optimize();
            if (mp.get(GRB.IntAttr.Status) == 3){
                this.feasible = false;
            }else {
//                obtain dual value
                GRBConstr[] cons = mp.getConstrs();
                double[] pi = new double[UserParam.nodeCount];
                pi[0] = 0.0;
                for (int i = 0; i < cons.length; i++) {
                    pi[i + 1] = cons[i].get(GRB.DoubleAttr.Pi);
                }
                int addedRoute = pricing(pi, 200);
                while (addedRoute>0) {
                    GRBColumn column = new GRBColumn();
//                    add new variable
                    for(Route newRoute: this.routes.subList(this.routes.size()-addedRoute, this.routes.size())){
                        column.clear();
                        double[] columnCoeff = newRoute.toColumn(UserParam.nodeCount);
                        column.addTerms(columnCoeff, mp.getConstrs());
                        String name = "y" + count;
                        count++;
                        mp.addVar(0.0, 1.0, newRoute.cost, GRB.CONTINUOUS, column, name);
                    }

                    mp.optimize();
                    for (int i = 0; i < cons.length; i++) {
                        pi[i + 1] = cons[i].get(GRB.DoubleAttr.Pi);
                    }
                    addedRoute = pricing(pi,50);
                }
                this.objVal = mp.get(GRB.DoubleAttr.ObjVal);
//                update lower bound
                if (this.objVal > this.lowerBound) {
                    this.lowerBound = this.objVal;
                }
                GRBVar[] vars = mp.getVars();
//                obtain the solution route and check fraction arc
                // mp.write("a.lp");
                for (int i = 0; i < vars.length; i++) {
                    double varVal = vars[i].get(GRB.DoubleAttr.X);
                    if (varVal > UserParam.tolerance) {
                        Route baseRoute = this.routes.get(i);
                        for (int j = 1; j < baseRoute.path.size() - 1; j++) {
                            List<Integer> arc = Arrays.asList(baseRoute.path.get(j), baseRoute.path.get(j + 1));
                            if (this.fractionArc.containsKey(arc)) {
                                this.fractionArc.compute(arc, (key, value) -> value += varVal);
                            } else {
                                this.fractionArc.put(arc, varVal);
                            }
                        }
                        this.solutionRoutes.add(baseRoute);
                    }
                }
                this.fractionArc = this.fractionArc.entrySet().stream().
                        filter((v) -> v.getValue() % 1 != 0).
                        collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            }
        }catch(GRBException e){
            System.out.println("Error code: " + e.getErrorCode() + ". " +
                    e.getMessage());
        }
    }

    public boolean pricing(double[] pi) throws GRBException{
        GRBEnv env1 = new GRBEnv();
        GRBModel sp = new GRBModel(env1);
        sp.set(GRB.IntParam.LogToConsole, 0);
        GRBVar[] x = sp.addVars(this.arcs.size(), GRB.BINARY);
        GRBVar[] u = sp.addVars(UserParam.nodeCount, GRB.CONTINUOUS);   //MTZ constraints
        GRBLinExpr expr = new GRBLinExpr();
        //  each arc subtract the corresponding cost of dual
        for(int i=0;i<x.length;i++){
            double edgeCost = UserParam.cost.get(this.arcs.get(i)) -
                    pi[this.arcs.get(i).get(1)];
            expr.addTerm(edgeCost, x[i]);
        }
        sp.setObjective(expr);
        //  flow conservation
        for(int i=0;i<UserParam.nodeCount;i++){
            GRBLinExpr expr1 = new GRBLinExpr();
            GRBLinExpr expr2 = new GRBLinExpr();
            //  index of the arc containing pre and subsequent node
            int thisNode = i;
            List<Integer> to = this.arcs.stream().filter(v->v.get(0) == thisNode).
                    map(v->this.arcs.indexOf(v)).collect(Collectors.toList());
            List<Integer> from = this.arcs.stream().filter(v->v.get(1)==thisNode).
                    map(v->this.arcs.indexOf(v)).collect(Collectors.toList());
            for(int j: to){
                expr1.addTerm(1.0, x[j]);
                // all customer can only be visited mostly once
            }
            for(int j: from){
                expr2.addTerm(1.0, x[j]);
            }
            sp.addConstr(expr1, GRB.EQUAL, expr2, "flow conservation");
            if(i!=0){
                sp.addConstr(expr1, GRB.LESS_EQUAL, 1.0, "single visit");
            }else{
                //  depot has to be visit
                sp.addConstr(expr1, GRB.EQUAL, 1.0, "single visit");
            }
        }
        sp.addConstr(u[0], GRB.EQUAL, 0.0, "MTZ");
        GRBLinExpr expr1 = new GRBLinExpr();
        for(int i=0;i<this.arcs.size();i++){
            expr.clear();
            int start = this.arcs.get(i).get(0);
            int end = this.arcs.get(i).get(1);
            if (end!=0){
                expr1.addTerm(UserParam.demand.get(end), x[i]);
                expr.addTerm(1.0, u[start]);
                expr.addTerm(-1.0, u[end]);
                expr.addTerm(UserParam.nodeCount, x[i]);
                sp.addConstr(expr, GRB.LESS_EQUAL, UserParam.nodeCount-1, "MTZ");
            }
        }
        sp.addConstr(expr1, GRB.LESS_EQUAL, UserParam.capacity, "capacity");
        sp.optimize();
        if(sp.get(GRB.DoubleAttr.ObjVal) < -UserParam.tolerance){
            //  extract path form the sub problem
            List<List<Integer>> arc = new ArrayList<List<Integer>>();
            //  extract arc with x=1
            for(int i=0;i<x.length;i++){
                if (x[i].get(GRB.DoubleAttr.X) > 0.5){
                    arc.add(this.arcs.get(i));
                }
            }
            List<Integer> route = new ArrayList<Integer>();
            route.add(0);
            while(arc.size() > 0){
                for (int i=0;i<arc.size();i++){
                    if (Objects.equals(arc.get(i).get(0), route.get(route.size() - 1))){
                        route.add(arc.get(i).get(1));
                        arc.remove(i);
                        break;
                    }
                }
            }
            route.remove(route.size()-1);
            this.routes.add(new Route(route));
            return true;
        }else{
            return false;
        }
    }

    public int pricing(double[] pi, int maxRoute){
        int addedRoute = 0;
        Map<Integer, List<Label>> Q = new HashMap<Integer, List<Label>>();//permanent labels
        Map<Integer, List<Label>> T = new HashMap<Integer, List<Label>>();//treated labels
        List<Label> initLabel = new ArrayList<Label>();
        initLabel.add(0, new Label(-1, 0.0, 0.0, Collections.singletonList(0)));
//          any feasible route with reduce cost<0 will be added to Q[0]
        Q.put(0, new ArrayList<Label>());
        T.put(0, initLabel);
        for (int i=1;i<UserParam.nodeCount;i++){
            Q.put(i, new ArrayList<Label>());
            T.put(i, new ArrayList<Label>());
        }
//        if there is any label untreated
        while(T.entrySet().stream().anyMatch(v-> v.getValue().size()>0)){
//            find lexicographically minimal label
            Map<Integer, Label> temp = new HashMap<Integer, Label>();
            for (int i: T.keySet()){
                if (T.get(i).size()>0) {
                    Collections.sort(T.get(i));
                    temp.put(i, T.get(i).get(0));
                }
            }
//             find the node that has the lexicographically minimal label
            int bestNode = temp.entrySet().stream().
                    sorted(Map.Entry.comparingByValue()).collect(Collectors.toList()).get(0).getKey();
//            extend labels
//            find the successor
            List<List<Integer>> arc = this.arcs.stream().
                    filter(v -> v.get(0)==bestNode).collect(Collectors.toList());
            for(Label label: T.get(bestNode)){
                for(List<Integer> a: arc){
//                      the label can't get reach the pre node
                    int successor = a.get(1);
                    if(successor != label.pre){
                        List<Integer> newPath = new ArrayList<Integer>(label.path);
//                        generate new label
                        Label newLabel;
                        if(successor != 0) {
                            newPath.add(successor);
                            newLabel = new Label(bestNode,
                                    label.cost+UserParam.cost.get(Arrays.asList(bestNode,successor))-pi[successor],
                                    label.weight + UserParam.demand.get(successor), newPath);
                        }else{
                            newLabel = new Label(bestNode,
                                    label.cost + UserParam.cost.get(Arrays.asList(bestNode, successor)),
                                    label.weight, newPath);
                        }
//                        check capacity constraint
                        if(newLabel.weight > UserParam.capacity){
                            continue;
                        }
//                        check if the successor == 0
                        if (successor == 0){
                            if (newLabel.cost < -0.1){
                                addedRoute++;
                                this.routes.add(new Route(newLabel.path));
//                                termination condition
                                if(addedRoute > maxRoute){
                                    return addedRoute;
                                }
                            }
                        }else {
//                            dominance check
                            List<Label> originLabels = Q.get(successor);
//                            first label of the node
                            if (originLabels.size() == 0) {
                                newLabel.rank = 2;
                            } else if (originLabels.stream().noneMatch(l -> l.compareTo(newLabel) < 0)) {
                                //  not dominated by any label
                                if (newLabel.weight + UserParam.demand.get(bestNode) > UserParam.capacity){
                                    newLabel.rank = 2;
                                }else{
                                    newLabel.rank = 1;
                                }
                            }else{
//                                being dominated by a label
                                List<Label> dominantLabels = originLabels.stream().filter(l-> l.compareTo(newLabel) < 0)
                                        .collect(Collectors.toList());
//                                no dominant label is strongly dominant
                                if(dominantLabels.stream().noneMatch(l->l.rank==2)){
                                    //no dominant labels has the same predecessor
                                    if(dominantLabels.stream().noneMatch(l->l.pre==bestNode)){
                                        newLabel.rank = 0;
                                    }else{
                                        continue;
                                    }
                                }else{
                                    continue;
                                }
                            }
//                            add the label to the sets
                            Q.get(successor).add(newLabel);
                            T.get(successor).add(newLabel);
                        }
                    }
                }
            }
            T.get(bestNode).clear();
        }
        return addedRoute;
    }
}
