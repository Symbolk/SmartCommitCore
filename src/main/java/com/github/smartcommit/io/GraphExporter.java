package com.github.smartcommit.io;

import com.github.smartcommit.model.graph.Edge;
import com.github.smartcommit.model.graph.Node;
import com.github.smartcommit.util.Utils;
import org.jgrapht.Graph;
import org.jgrapht.io.ComponentAttributeProvider;
import org.jgrapht.io.ComponentNameProvider;
import org.jgrapht.io.DOTExporter;
import org.jgrapht.io.ExportException;

import java.io.StringWriter;
import java.io.Writer;

public class GraphExporter {
  /** Export a graph into DOT format. */
  public static String exportAsDot(Graph<Node, Edge> graph) {
    try {
      // use helper classes to define how vertices should be rendered,
      // adhering to the DOT language restrictions
      ComponentNameProvider<Node> vertexIdProvider = node -> node.getId().toString();
      ComponentNameProvider<Node> vertexLabelProvider = node -> node.getIdentifier();
      ComponentNameProvider<Edge> edgeLabelProvider = edge -> edge.getType().asString();
      org.jgrapht.io.GraphExporter<Node, Edge> exporter =
          new DOTExporter<>(vertexIdProvider, vertexLabelProvider, edgeLabelProvider);
      Writer writer = new StringWriter();
      exporter.exportGraph(graph, writer);
      return writer.toString();

    } catch (ExportException e) {
      e.printStackTrace();
      return "";
    }
  }

  public static String exportAsDotWithType(Graph<Node, Edge> graph) {
    try {
      // use helper classes to define how vertices should be rendered,
      // adhering to the DOT language restrictions
      ComponentNameProvider<Node> vertexIdProvider = node -> node.getId().toString();
      ComponentNameProvider<Node> vertexLabelProvider =
          node ->
              node.isInDiffHunk
                  ? node.getIdentifier() + "(" + node.diffHunkIndex + ")"
                  : node.getIdentifier();
      ComponentAttributeProvider<Node> vertexAttributeProvider = new TypeProvider();

      ComponentNameProvider<Edge> edgeLabelProvider =
          edge -> edge.getType().asString() + "(" + edge.getWeight() + ")";
      ComponentAttributeProvider<Edge> edgeAttributeProvider = new TypeProvider();
      org.jgrapht.io.GraphExporter<Node, Edge> exporter =
          new DOTExporter<>(
              vertexIdProvider,
              vertexLabelProvider,
              edgeLabelProvider,
              vertexAttributeProvider,
              edgeAttributeProvider);
      Writer writer = new StringWriter();
      exporter.exportGraph(graph, writer);
      return writer.toString();

    } catch (ExportException e) {
      e.printStackTrace();
      return "";
    }
  }

  /**
   * Print the graph to console for debugging
   *
   * @param graph
   */
  public static void printAsDot(Graph<Node, Edge> graph) {
    System.out.println(exportAsDotWithType(graph));
  }

  /**
   * Save the exported dot to file
   *
   * @param graph
   */
  public static void saveAsDot(Graph<Node, Edge> graph, String filePath) {
    Utils.writeStringToFile(exportAsDotWithType(graph), filePath);
  }

  public static void printVertexAndEdge(Graph<Node, Edge> graph) {
    for (Node node : graph.vertexSet()) {
      System.out.println(node);
    }
    System.out.println("------------------------------");
    for (Edge edge : graph.edgeSet()) {
      Node source = graph.getEdgeSource(edge);
      Node target = graph.getEdgeTarget(edge);
      System.out.println(
          source.getIdentifier() + " " + edge.getType().asString() + " " + target.getIdentifier());
    }
    System.out.println("------------------------------");
  }
}
