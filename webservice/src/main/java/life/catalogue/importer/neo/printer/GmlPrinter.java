package life.catalogue.importer.neo.printer;


import com.google.common.collect.Lists;
import life.catalogue.importer.neo.model.RankedUsage;
import life.catalogue.importer.neo.model.RelType;
import life.catalogue.importer.neo.traverse.UsageRankEvaluator;
import org.gbif.nameparser.api.Rank;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Relationship;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * Expects no pro parte relations in the walker!
 * <p>
 * https://en.wikipedia.org/wiki/Graph_Modelling_Language
 * See http://www.fim.uni-passau.de/fileadmin/files/lehrstuhl/brandenburg/projekte/gml/gml-technical-report.pdf
 */
public class GmlPrinter extends BasePrinter{
  private final Writer writer;
  private final List<Edge> edges = Lists.newArrayList();
  private final boolean strictTree;
  private final UsageRankEvaluator rankEvaluator;

  /**
   * @param strictTree if true omit any pro parte and basionym relations to force a strict tree
   */
  public GmlPrinter(Writer writer, @Nullable Rank rankThreshold, boolean strictTree) {
    super(true);
    this.strictTree = strictTree;
    this.writer = writer;
    this.rankEvaluator = new UsageRankEvaluator(rankThreshold);
    printHeader();
  }
  
  private static class Edge {
    public final long source;
    public final long target;
    
    public Edge(long source, long target) {
      this.source = source;
      this.target = target;
    }
    
    public static Edge create(Relationship rel) {
      return new Edge(rel.getStartNode().getId(), rel.getEndNode().getId());
    }
    
    public static Edge inverse(Relationship rel) {
      return new Edge(rel.getEndNode().getId(), rel.getStartNode().getId());
    }
    
    void print(Writer writer) throws IOException {
      writer.append("  edge [\n")
          .append("    source ")
          .append(String.valueOf(source))
          .append("\n    target ")
          .append(String.valueOf(target))
          .append("\n  ]\n");
    }
  }
  
  private void printHeader() {
    try {
      writer.write("graph [\n");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  @Override
  public void close() {
    try {
      for (Edge e : edges) {
        e.print(writer);
      }
      writer.write("]\n");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  @Override
  public void start(RankedUsage u) {
    try {
      String label = String.format("%s [%s]",
          u.getNameWithAuthor(),
          u.rank.name().toLowerCase()
      );
      writer.write("  node [\n");
      writer.write("    value " + u.usageNode.getId() + "\n");
      writer.write("    label \"" + label + "\"\n");
      writer.write("  ]\n");
      
      // keep edges with minimal footprint for later writes, they need to go at the very end!
      
      // for a strict tree we only use parent and synonym of relations
      // synonym_of relations are inversed so the tree strictly points into one direction
      if (strictTree) {
        for (Relationship rel : u.usageNode.getRelationships(RelType.PARENT_OF, Direction.OUTGOING)) {
          if (rankEvaluator.evaluateNode(rel.getOtherNode(u.usageNode))) {
            edges.add(Edge.create(rel));
          }
        }
        for (Relationship rel : u.usageNode.getRelationships(RelType.SYNONYM_OF, Direction.OUTGOING)) {
          if (rankEvaluator.evaluateNode(rel.getOtherNode(u.usageNode))) {
            edges.add(Edge.inverse(rel));
          }
        }
        
      } else {
        for (Relationship rel : u.usageNode.getRelationships(Direction.OUTGOING)) {
          if (rankEvaluator.evaluateNode(rel.getOtherNode(u.usageNode))) {
            edges.add(Edge.create(rel));
          }
        }
      }
      
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

}
