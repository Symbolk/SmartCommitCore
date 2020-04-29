package com.github.smartcommit.io;

import com.github.smartcommit.model.diffgraph.DiffEdge;
import com.github.smartcommit.model.diffgraph.DiffNode;
import org.jgrapht.Graph;
import org.jgrapht.io.ComponentAttributeProvider;
import org.jgrapht.io.ComponentNameProvider;
import org.jgrapht.io.DOTExporter;
import org.jgrapht.io.ExportException;

import java.io.StringWriter;
import java.io.Writer;

public class DiffGraphExporter {
  public static String exportAsDotWithType(Graph<DiffNode, DiffEdge> graph) {
    try {
      // use helper classes to define how vertices should be rendered,
      // adhering to the DOT language restrictions
      ComponentNameProvider<DiffNode> vertexIdProvider = diffNode -> diffNode.getId().toString();
      ComponentNameProvider<DiffNode> vertexLabelProvider = diffNode -> diffNode.getIndex();
      ComponentAttributeProvider<DiffNode> vertexAttributeProvider = new DiffTypeProvider();

      ComponentNameProvider<DiffEdge> edgeLabelProvider =
          edge -> edge.getType().asString() + "(" + edge.getWeight().toString() + ")";
      ComponentAttributeProvider<DiffEdge> edgeAttributeProvider = new DiffTypeProvider();
      org.jgrapht.io.GraphExporter<DiffNode, DiffEdge> exporter =
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
}
