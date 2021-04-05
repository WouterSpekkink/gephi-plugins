/*
* To change this license header, choose License Headers in Project Properties.
* To change this template file, choose Tools | Templates
* and open the template in the editor.
*/

package org.freetime.plugins.metric.trophicincoherence;

import java.util.LinkedList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.gephi.graph.api.Graph;
import org.gephi.graph.api.GraphModel;
import org.gephi.graph.api.Column;
import org.gephi.graph.api.Node;
import org.gephi.graph.api.Table;

import org.gephi.graph.api.DirectedGraph;
import org.gephi.graph.api.GraphController;

import org.gephi.graph.api.NodeIterable;
import org.gephi.graph.api.UndirectedGraph;
import org.gephi.statistics.plugin.ConnectedComponents;
import org.gephi.statistics.spi.Statistics;
import org.openide.util.Lookup;

import org.la4j.Matrix;
import org.la4j.Vector;
import org.la4j.matrix.MatrixFactory;
import org.la4j.vector.dense.BasicVector;
import org.la4j.vector.DenseVector;
import org.la4j.matrix.dense.Basic2DMatrix;

import org.la4j.linear.GaussianSolver;
/**
 *
 * @author wouter
 */

public class TrophicIncoherence implements Statistics {
    
    public static final String TROPHICLEVEL = "Trophic_Level";
    public static final String COMPONENT = "Component";
    public static final String POTENTIAL = "Potential_Flow";
    public static final String CIRCULAR = "Circular_Flow";
    private boolean isDirected;
    private boolean negativeWeights = false;
    private boolean singularWarning = false;
    public boolean averaged = false;
    private Map<Integer, Double> incoherenceMap = new HashMap();
    public String report;
    private double incoherenceStatistic;
    
    public TrophicIncoherence() {
        GraphController graphController = Lookup.getDefault().lookup(GraphController.class);
        if (graphController != null && graphController.getGraphModel() != null) {
            isDirected = graphController.getGraphModel().isDirected();
        }
    }
    
    @Override
    public void execute(GraphModel graphModel) {
        Graph graph;
        isDirected = graphModel.isDirected();
        if (isDirected) {
            graph = graphModel.getDirectedGraphVisible();
        } else {
            graph = graphModel.getUndirectedGraphVisible();
        }
        execute(graph, graphModel);
    }
    
    // This is where the actual work is done.
    public void execute(Graph hgraph, GraphModel graphModel) {
        // Let's make a default report in case something went wrong:
        report = "<HTML? <BODY> <h1>Trophic Incoherence</h1> "
                + "<hr>"
                + "<br> Something went wrong and the function did not fully execute. <br />"
                + "<br> <br />"
                + "</BODY></HTML>";
        // We check if the graph is directed. If not, we report this to the user and return.
        if (graphModel.isDirected()) {
            // Add column for trophic levels if not already there
            Table nodeTable = graphModel.getNodeTable();
            Table edgeTable = graphModel.getEdgeTable();
            Column trophcol = nodeTable.getColumn(TROPHICLEVEL);
            Column compcol = nodeTable.getColumn(COMPONENT);
            Column potcol = edgeTable.getColumn(POTENTIAL);
            Column circol = edgeTable.getColumn(CIRCULAR);
            if (trophcol == null) {
                trophcol = nodeTable.addColumn(TROPHICLEVEL, "Trophic_Level", Double.class, null);
            }
            if (potcol == null) {
                potcol = edgeTable.addColumn(POTENTIAL, "Potential_Flow", Double.class, null);
            }
            if (circol == null) {
                circol = edgeTable.addColumn(CIRCULAR, "Circular_Flow", Double.class, null);
            }
            // Lock the graph while we are working with it.
            hgraph.readLock();
            // We want to check if there are weakly connected components.
            // We treat the graph as undirected for this step.
            UndirectedGraph undirectedGraph = graphModel.getUndirectedGraphVisible();
            // Let us just use the built-in Gephi functions for getting components.
            ConnectedComponents componentsModule = new ConnectedComponents();
            HashMap<Node, Integer> indices = componentsModule.createIndicesMap(undirectedGraph);
            LinkedList<LinkedList<Node>> components = componentsModule.computeWeaklyConnectedComponents(undirectedGraph, indices);
            
            // Add component variable if we have multiple components.
            if (components.size() > 1)
            {
                if (compcol == null) {
                    compcol = nodeTable.addColumn(COMPONENT, "Component", Integer.class, 0);
                }
                int c = 0;
                for (LinkedList<Node> component : components) {
                    for (Node currentNode : component) {
                        currentNode.setAttribute(compcol, c);
                    }
                    c++;
                }
            }
            
            // Now we treat each component as a separate network.
            // Let's keep track of component numbers, starting with 1.
            int c = 0;
            for (LinkedList<Node> component : components) {
                // Ignore components that are singular systems
                if (component.size() > 1 )
                {
                    // Now we reconstruct our adjacency matrix
                    // We make an empty matrix first.
                    double[][] adj_mat = new double[component.size()][component.size()];
                    // And let's initialize it to be sure
                    for (int x = 0; x < component.size(); x++) {
                        for (int y = 0; y < component.size(); y++) {
                            adj_mat[x][y] = 0.0;
                        }
                    }
                    
                    // We will create an array of nodes of the current component.
                    Node[] nodes = new Node[component.size()];
                    for (int i =0; i < component.size(); i++) {
                        nodes[i] = component.get(i);
                    }
                    
                    // We can already get the inweight and outweight while we
                    // make the adjacency matrix
                    double[] inweight = new double[component.size()];
                    double[] outweight = new double[component.size()];
                    
                    // Now we iterate through all nodes
                    for (int i = 0; i < component.size(); i++) {
                        // First initialize the attribute
                        nodes[i].setAttribute(trophcol, 0.0);
                        // Now let us find all targets of this node and iterate through them
                        for (int j = 0; j < component.size(); j++) {
                            if (hgraph.getEdge(nodes[i], nodes[j]) != null) {
                                // Let's get the weight of the edge between these nodes
                                double weight = hgraph.getEdge(nodes[i], nodes[j]).getWeight();
                                if (weight < 0)
                                {
                                    // If we find a negative weight, set the corresponding boolean to true
                                    negativeWeights = true;
                                    // ... and turn it into a positive weight.
                                    weight *= -1;
                                }
                                outweight[i] += weight;
                                inweight[j] += weight;
                                // And then fill the corresponding cell of the matrix
                                adj_mat[i][j] = weight;
                            }
                        }
                    }
                    
                    // Let us create the v and w variables
                    double[] vA = new double[component.size()];
                    double[] wA = new double[component.size()];
                    for (int i = 0; i < component.size(); i++) {
                        vA[i] = inweight[i] - outweight[i];
                        wA[i] = inweight[i] + outweight[i];
                    }
                    Vector v = new BasicVector(vA);
                    Vector w = new BasicVector(wA);
                    
                    // Let's create the diagonal matrix from w.
                    Matrix diag = new Basic2DMatrix(component.size(),component.size());
                    for (int i = 0; i < component.size(); i++) {
                        diag.set(i, i, w.get(i));
                    }
                    
                    // Let us now create matrices from our results.
                    Matrix adj = new Basic2DMatrix(adj_mat);
                    Matrix sum = adj.add(adj.transpose());
                    
                    // And now we prepare our matrix for the linear solver.
                    Matrix L = diag.subtract(sum);
                    L.set(0, 0, 0.0);
                    
                    // The vector for results
                    Vector h;
                    
                    // The actual solver from the la4j library
                    GaussianSolver solver = new GaussianSolver(L);
                    h = solver.solve(v);
                    h = h.subtract(h.min());
                    DenseVector hD = h.toDenseVector();
                    double[] results = hD.toArray();
                    for (int i = 0; i < component.size(); i++) {
                        nodes[i].setAttribute(trophcol, results[i]);
                    }
                    
                    // An attempt to also calculate the trophic incoherence
                    // We can also use this loop to get the potential flow network
                    
                    // First initialize the numinator and denominator
                    double numerator = 0.0;
                    double denominator = 0.0;
                    for (int i = 0; i < component.size(); i++) {
                        for (int j = 0; j < component.size(); j++) {
                            // It only makes sense to do this if there is an edge
                            if (hgraph.getEdge(nodes[i], nodes[j]) != null) {
                                // Get the weight of the edge between the two nodes
                                double weight = hgraph.getEdge(nodes[i], nodes[j]).getWeight();
                                // Convert negative weights to positive weights.
                                if (weight < 0) {
                                    weight *= -1;
                                }
                                double pflow = weight * (results[j]-results[i]);
                                double cflow = weight - pflow;
                                hgraph.getEdge(nodes[i], nodes[j]).setAttribute(potcol, pflow);
                                hgraph.getEdge(nodes[i], nodes[j]).setAttribute(circol, cflow);
                                // Let's immediately add this to the sum of weights (denominator)
                                denominator += weight;
                                // Get the trophic level of the nodes
                                double hi = results[i];
                                double hj = results[j];
                                // Then we can add to the numerator
                                numerator += weight * ((hj - hi - 1) * (hj -hi - 1));
                            }
                        }
                    }
                    double incoherence= numerator / denominator;
                    
                    // Keep track of trophic incoherence for this component
                    incoherenceMap.put(c, incoherence);
                    
                    // If we want to set the average trophic level to 0, we need to do the below too
                    // If not, the next part can be skipped.
                    if (averaged) {
                        // First we need to find the minimum, maximum and average h
                        double min = -1;
                        double max = -1;
                        double total = 0.0;
                        for (int i = 0; i < results.length; i++) {
                            total += results[i];
                            if (min == -1) {
                                min = results[i];
                            } else if (results[i] < min) {
                                min = results[i];
                            }
                            if (max == -1) {
                                max = results[i];
                            } else if (results[i] > max) {
                                max = results[i];
                            }
                        }
                        double h_mean = total / (double) results.length;
                        // Then we can 
                        for (int i = 0; i < component.size(); i++) {
                            double hv = results[i] - min;
                            hv = hv - h_mean;
                            hv = hv - (1/2) * (min + max); 
                            nodes[i].setAttribute(trophcol, hv);
                        }
                    }
                }
                else {
                    singularWarning = true;
                }
                // Increment component number; Last thing we do in this loop.
                c++;
            }
            // Unlock the graph, since we're finished.
            hgraph.readUnlock();
            // What the report is upon success.
            report = "<HTML? <BODY> <h1>Trophic Incoherence</h1> "
                    + "<hr>"
                    + "<br> The trophic levels of each node are reported in the Trophic_Levels column (see data laboratory). <br />";
            // We need to add the trophic incoherence for each component separately.
            Iterator<Map.Entry<Integer, Double>> entrySet = incoherenceMap.entrySet().iterator();
            while (entrySet.hasNext()) {
                Map.Entry<Integer, Double> entry = entrySet.next();
                report += "<br>" + "Trophic Incoherence of component " + entry.getKey() + ":  " + entry.getValue() + "<br />";
            }
            report += "<br> </br>";
            if (singularWarning) {
                report += "<br> Components with single nodes were found. "
                        + "These components are skipped in the calculation of trophic levels and trophic incoherence.<br />"
                        + "<br> <br />";
            }
            if (negativeWeights) {
                report += "<br> Please note that negative edge weights are treated as positive edge weights by this plugin. "
                        + "This means that positive and negative edge weights contribute in the same way to determining the overall "
                        + "direction of flows in the network.<br />"
                        + "<br><br />";
            }
            report += "</BODY></HTML>";
        } else {
            // Report if graph is undirected.
            report = "<HTML? <BODY> <h1>Trophic Incoherence</h1> "
                    + "<hr>"
                    + "<br> The Trophic Incoherence metric only applies to directed graphs. <br />"
                    + "<br> <br />";
            report += "</BODY></HTML>";
        }
        // Unlock the graph after we're finished.q
        
    }
    
    public void setDirected(boolean isDirected) {
        this.isDirected = isDirected;
    }
    
    public double getIncoherence()
    {
        if (incoherenceMap.size() == 1) {
            Map.Entry<Integer, Double> entry = incoherenceMap.entrySet().iterator().next();
            return entry.getValue();
        } else {
            return -1.0;
        }
    }
    
    public void setAveraged(Boolean averaged) {
        this.averaged = averaged;
    }
    
    public Boolean isAveraged() {
        return averaged;
    }
    
    @Override
    public String getReport() {
        // Return report.
        return report;
    }
}
