package life.catalogue.printer;

import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.TreeTraversalParameter;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.dao.TaxonCounter;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSessionFactory;

import org.gbif.txtree.SimpleTreeNode;
import org.gbif.txtree.Tree;

/**
 * Print an entire dataset in the indented text format used by TxtPrinter.
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
public class TextTreePrinter extends AbstractTreePrinter {
  private static final int indentation = 2;
  private boolean showIDs;

  public TextTreePrinter(TreeTraversalParameter params, Set<Rank> ranks, Rank countRank, TaxonCounter taxonCounter, SqlSessionFactory factory, Writer writer) {
    super(params, ranks, countRank, taxonCounter, factory, writer);
  }


  public void showIDs() {
    this.showIDs = true;
  }


  protected void start(SimpleName u) throws IOException {
    writer.write(StringUtils.repeat(' ', level * indentation));
    if (u.isExtinct()) {
      writer.write(Tree.EXTINCT_SYMBOL);
    }
    if (u.getStatus() != null && u.getStatus().isSynonym()) {
      writer.write(Tree.SYNONYM_SYMBOL);
    }
    //TODO: flag basionyms
    writer.write(u.getName());
    if (u.getAuthorship() != null) {
      writer.write(" ");
      writer.write(u.getAuthorship());
    }
    writer.write(" [");
    Rank r = ObjectUtils.coalesce(u.getRank(), Rank.UNRANKED);
    writer.write(r.name().toLowerCase());
    writer.write("]");

    var infos = infos(u);
    if (!infos.isEmpty()) {
      writer.write(" {");
      writer.write(String.join("|", infos));
      writer.write("}");
    }

    writer.write('\n');
  }

  protected void end(SimpleName u) {
    //nothing
  }

  /**
   * @return list of infos to be appended in brackets after the name
   * @param u
   */
  private List<String> infos(SimpleName u){
    List<String> infos = new ArrayList<>();
    if (showIDs) {
      infos.add("ID=" + u.getId());
    }
    if (countRank != null) {
      infos.add("NUM_"+countRank.name() + "=" + taxonCount);
    }
    return infos;
  }

}
