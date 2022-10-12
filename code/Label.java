package BnP_Framework;
import java.util.ArrayList;
import java.util.List;
/*
The class is used when solving sub problem of column generation, it records the status of the path
*/
public class Label implements Comparable<Label> {
    int pre;
    double cost;
    double weight;
    int rank;
    List<Integer> path = new ArrayList<Integer>();

    public Label(int pre, double cost, double weight, List<Integer> path) {
        this.pre = pre;
        this.cost = cost;
        this.weight = weight;
        this.path = path;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public double getCost() {
        return this.cost;
    }

    /*
    return -1: label1 dominate label2
    return 1: label2 dominate label1
     */
    @Override
    public int compareTo(Label other) {
        if (this.cost < other.cost){
            return -1;
        }else if(this.cost== other.cost){
            if(this.weight <= other.weight){
                return -1;
            }else{
                return 1;
            }
        }else{
            return 1;
        }
    }

    @Override
    public String toString() {
        return "pre=" + pre + ", cost=" + cost + ", weight=" + weight +
                ", path=" + path;
    }
}
