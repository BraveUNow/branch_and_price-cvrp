package BnP_Framework;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/*
the class generator parse a .json file and store the parameters of the model, includes arcs,
    edge costs, demand, vehicle capacity, vertex number, algorithm tolerance(labeling)
the class instance records the global value of the algorithm, which includes lower(upper) bound, solution routes
*/
public class UserParam {
    static List<List<Integer>> arcs = new ArrayList<List<Integer>>();
    static Map<List<Integer>, Double> cost = new HashMap<List<Integer>, Double>();
    static Map<Integer, Double> demand = new HashMap<Integer, Double>();
    static double capacity;
    static int nodeCount;
    static double tolerance = 1e-4;
    double upperBound;
    List<Route> bestRoutes;

    UserParam(String filePath){
        Path path = Paths.get(filePath);
        String jsonString = "";
        try{
            jsonString = new String(Files.readAllBytes(path));
        }catch (IOException e) {
            System.out.println("no such files");
        }
        JSONObject data = JSONObject.fromObject(jsonString);
        // parse demand
        String key;
        double value;
        JSONObject jsonObject = data.getJSONObject("demand");
        Iterator<String> keys = jsonObject.keys();
        while (keys.hasNext()){
            key = keys.next();
            value = Double.parseDouble(jsonObject.get(key).toString());
            demand.put(Integer.parseInt(key), value);
        }
        // parse arcs and costs
        JSONArray arcsArray = data.getJSONArray("arcs");
        JSONArray arcsCost = data.getJSONArray("cost");
        for (int i =0;i<arcsArray.size();i++){
            JSONArray tempArray = JSONArray.fromObject(arcsArray.get(i));
            List<Integer> arc = Arrays.asList(tempArray.getInt(0), tempArray.getInt(1));
            arcs.add(arc);
            cost.put(arc, arcsCost.getDouble(i));
        }
        // parse capacity
        capacity = Double.parseDouble(data.get("capacity").toString());
        nodeCount = demand.size()+1;
        // initialize bound
        upperBound = Double.POSITIVE_INFINITY;
    }

}
