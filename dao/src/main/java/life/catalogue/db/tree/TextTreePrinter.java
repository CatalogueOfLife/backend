package life.catalogue.db.tree;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Sector;
import life.catalogue.api.model.SimpleName;
import life.catalogue.db.mapper.SectorMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;

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
public class TextTreePrinter extends SimpleUsageTreePrinter {
  public static final String SYNONYM_SYMBOL = "*";
  public static final String BASIONYM_SYMBOL = "$";
  
  private static final int indentation = 2;
  private final Writer writer;

  /**
   * @param sectorKey optional sectorKey to restrict printed tree to
   */
  private TextTreePrinter(int datasetKey, Integer sectorKey, String startID, Set<Rank> ranks, SqlSessionFactory factory, Writer writer) {
    super(datasetKey, sectorKey, startID, ranks, factory);
    this.writer = writer;
  }
  
  public static TextTreePrinter dataset(int datasetKey, SqlSessionFactory factory, Writer writer) {
    return new TextTreePrinter(datasetKey, null, null, null, factory, writer);
  }
  
  public static TextTreePrinter dataset(int datasetKey, String startID, Set<Rank> ranks, SqlSessionFactory factory, Writer writer) {
    return new TextTreePrinter(datasetKey, null, startID, ranks, factory, writer);
  }

  /**
   * Prints a sector from the given catalogue.
   */
  public static TextTreePrinter sector(final DSID<Integer> sectorKey, SqlSessionFactory factory, Writer writer) {
    try (SqlSession session = factory.openSession(true)) {
      Sector s = session.getMapper(SectorMapper.class).get(sectorKey);
      return new TextTreePrinter(sectorKey.getDatasetKey(), sectorKey.getId(), s.getTarget().getId(), null, factory, writer);
    }
  }

  @Override
  public void flush() throws IOException {
    writer.flush();
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
    if (u.getRank() != null) {
      writer.write(" [");
      writer.write(u.getRank().name().toLowerCase());
      writer.write("]");
    }

    writer.write('\n');
  }

  protected void end(SimpleName u) {
    //nothing
  }
  
}
