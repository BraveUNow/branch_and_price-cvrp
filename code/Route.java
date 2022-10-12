package BnP_Framework;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
/*
Each instance of this class is a representative of the route
    'path' attribute records the path in List, such as [0,1,2,3], which means 0->1->2->3->0
    'cost' attribute records the visiting cost of this path
    toColumn(int n) transform a path into a column of LP, it returns a double array with length n-1
    containsArc() and notContainsArc() check if the route needs to be deleted in the branching node
 */
public class Route {
    List<Integer> path;
    double cost;
    Route(List<Integer> path, double cost){
        this.path = path;
        this.cost = cost;
    }
    Route(List<Integer> path){
        this.path = path;
        calCost(UserParam.cost);
    }
    // transform the route in to column of the arc-flow formulation, the number of customer is needed
    // the function is for single-depot problem, since we default 0 is depot and 1:n is the customer
    // you can also override if for the specified problem
    public double[] toColumn(int n){
        double[] column = new double[n-1];
        for (int i=1;i<n;i++){
            if (this.path.contains(i)){
                int visitedNode = i;
                column[i-1] = this.path.stream().filter(v->v==visitedNode).count();
            }else{
                column[i-1] = 0.0;
            }
        }
        return column;
    }
    public void calCost(Map<List<Integer>, Double> cost){
        this.cost = 0.0;
        for (int i=0;i<this.path.size()-1;i++){
            List<Integer> arc = Arrays.asList(this.path.get(i), this.path.get(i+1));
            this.cost += cost.get(arc);
        }
        this.cost += cost.get(Arrays.asList(this.path.get(this.path.size()-1), 0));
    }
    @Override
    public String toString(){
        return this.path.toString();
    }
    public boolean containsArc(List<Integer> arc){
        int start = arc.get(0);
        int end = arc.get(1);
        if(this.path.contains(start) && this.path.contains(end)){
            if(this.path.indexOf(end) - this.path.indexOf(start) == 1){
                return true;
            }else{
                return false;
            }
        }else{
            return false;
        }
    }
    public boolean notContainsArc(List<Integer> arc){
        int start = arc.get(0);
        int end = arc.get(1);
        if(this.path.contains(start)) {
            if(this.path.indexOf(start) != this.path.size()-1){
                if(this.path.get(this.path.indexOf(start)+1) != end){
                    return true;
                }else{
                    return false;
                }
            }else{
//                no fraction arc to 0
                return true;
            }
        }else if(this.path.contains(end)) {
            if (this.path.get(this.path.indexOf(end) - 1) != start) {
                return true;
            } else {
                return false;
            }
        }else{
            return false;
        }
    }
}
