package life.catalogue.db.tree;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Sector;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.dao.TaxonCounter;
import life.catalogue.db.mapper.SectorMapper;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

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
  public static final String SYNONYM_SYMBOL = "*";
  public static final String BASIONYM_SYMBOL = "$";
  
  private static final int indentation = 2;
  private boolean showIDs;

  /**
   * @param datasetKey
   * @param sectorKey    optional sectorKey to restrict printed tree to
   * @param startID
   * @param synonyms
   * @param ranks
   * @param countRank    the rank to be used when counting with the taxonCounter
   * @param taxonCounter
   * @param factory
   * @param writer
   */
  public TextTreePrinter(int datasetKey, Integer sectorKey, String startID, boolean synonyms, Set<Rank> ranks, Rank countRank, TaxonCounter taxonCounter, SqlSessionFactory factory, Writer writer) {
    super(datasetKey, sectorKey, startID, synonyms, ranks, countRank, taxonCounter, factory, writer);
  }


  public void showIDs() {
    this.showIDs = true;
  }


  protected void start(SimpleName u) throws IOException {
    writer.write(StringUtils.repeat(' ', level * indentation));
    if (u.getStatus() != null && u.getStatus().isSynonym()) {
      writer.write(SYNONYM_SYMBOL);
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
    if (countRank != null) {
      infos.add(JsonTreePrinter.countRankPropertyName(countRank) + "=" + taxonCount);
    }
    if (showIDs) {
      infos.add("ID=" + u.getId());
    }
    return infos;
  }

}
