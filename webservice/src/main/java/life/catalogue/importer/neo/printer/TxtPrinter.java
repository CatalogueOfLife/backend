package life.catalogue.importer.neo.printer;

import java.io.IOException;
import java.io.Writer;

import life.catalogue.api.txtree.Tree;
import life.catalogue.importer.neo.model.RankedUsage;
import life.catalogue.importer.neo.model.RelType;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.parboiled.common.StringUtils;

/**
 * A handler that can be used with the TaxonWalker to print a neo4j taxonomy in a simple nested text structure.
 * Synonyms are prefixed with an asterisk *,
 * Pro parte synoynms with a double asterisk **,
 * basionyms are prefixed by a $ and listed first in the synonymy.
 * <p>
 * Ranks are given in brackets after the scientific name
 * <p>
 * A basic example tree would look like this:
 * <pre>
 * Plantae [kingdom]
 * Compositae Giseke [family]
 * Asteraceae [family]
 * Artemisia L. [genus]
 * Artemisia elatior (Torr. & A. Gray) Rydb.
 * $Artemisia tilesii var. elatior Torr. & A. Gray
 * $Artemisia rupestre Schrank L. [species]
 * Absinthium rupestre (L.) Schrank [species]
 * Absinthium viridifolium var. rupestre (L.) Besser
 * </pre>
 */
public class TxtPrinter extends BasePrinter {

  private static final int indentation = 2;
  private int level = 0;
  private final Writer writer;
  
  public TxtPrinter(Writer writer) {
    super(true);
    this.writer = writer;
  }
  
  @Override
  public void start(RankedUsage u) {
    print(u);
    level++;
  }
  
  @Override
  public void end(Node n) {
    level--;
  }

  private void print(RankedUsage u) {
    try {
      //writer.write(String.valueOf(n.getId()));
      writer.write(StringUtils.repeat(' ', level * indentation));
      if (u.isSynonym()) {
        writer.write(Tree.SYNONYM_SYMBOL);
        if (u.usageNode.getDegree(RelType.SYNONYM_OF, Direction.OUTGOING) > 1) {
          // flag pro parte synonyms with an extra asterisk
          writer.write(Tree.SYNONYM_SYMBOL);
        }
      }
      if (u.nameNode.hasRelationship(RelType.HAS_BASIONYM, Direction.INCOMING)) {
        writer.write(Tree.BASIONYM_SYMBOL);
      }
      writer.write(u.name);
      if (!org.apache.commons.lang3.StringUtils.isBlank(u.author)) {
        writer.write(" ");
        writer.write(u.author);
      }
      if (u.rank != null) {
        writer.write(" [");
        writer.write(u.rank.name().toLowerCase());
        writer.write("]");
      }
      writer.write("\n");
      
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
}
