package life.catalogue.db.tree;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Sector;
import life.catalogue.api.model.SimpleName;
import life.catalogue.db.mapper.SectorMapper;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import javax.annotation.Nullable;

/**
 * Print an entire dataset in a nested SimpleName json array.
 */
public class JsonTreePrinter extends SimpleUsageTreePrinter {
  private static final int indentation = 2;
  private final Writer writer;
  private EVENT last;
  private enum EVENT {START, END}

  /**
   * @param sectorKey optional sectorKey to restrict printed tree to
   */
  protected JsonTreePrinter(int datasetKey, Integer sectorKey, String startID, boolean synonyms, Set<Rank> ranks, @Nullable Rank countRank, @Nullable TaxonCounter taxonCounter, SqlSessionFactory factory, Writer writer) {
    super(datasetKey, sectorKey, startID, synonyms, ranks, countRank, taxonCounter, factory);
    this.writer = writer;
  }

  /**
   * Prints the entire dataset
   */
  public static JsonTreePrinter dataset(int datasetKey, SqlSessionFactory factory, Writer writer) {
    return new JsonTreePrinter(datasetKey, null, null, true, null, null, null, factory, writer);
  }

  public static JsonTreePrinter dataset(int datasetKey, @Nullable String startID, boolean synonyms, Set<Rank> ranks, @Nullable Rank countRank, @Nullable TaxonCounter taxonCounter, SqlSessionFactory factory, Writer writer) {
    return new JsonTreePrinter(datasetKey, null, startID, synonyms, ranks, countRank, taxonCounter, factory, writer);
  }

  /**
   * Prints a sector from the given catalogue.
   */
  public static JsonTreePrinter sector(final DSID<Integer> sectorKey, SqlSessionFactory factory, Writer writer) {
    try (SqlSession session = factory.openSession(true)) {
      Sector s = session.getMapper(SectorMapper.class).get(sectorKey);
      return new JsonTreePrinter(sectorKey.getDatasetKey(), sectorKey.getId(), s.getTarget().getId(), true, null, null, null, factory, writer);
    }
  }

  @Override
  public int print() throws IOException {
    writer.write("[");
    level = 1;
    int count = super.print();
    writer.write("\n]");
    return count;
  }

  public static String countRankPropertyName(Rank rank) {
    return rank.name().toLowerCase();
  }

  protected void start(SimpleName u) throws IOException {
    u.setParent(null);
    if (last == EVENT.END) {
      writer.write(",");
    }
    writer.write("\n");
    writer.write(StringUtils.repeat(' ', level * indentation));
    String json = ApiModule.MAPPER.writeValueAsString(u);
    writer.write(json.substring(0, json.length()-1));
    if (countRank != null) {
      writer.write(",\"" + countRankPropertyName(countRank) + "\":" + taxonCount);
    }
    writer.write(",\"children\":[");
    last = EVENT.START;
  }

  protected void end(SimpleName u) throws IOException {
    if (last == EVENT.END) {
      writer.write("\n");
      writer.write(StringUtils.repeat(' ', (level-1) * indentation));
    }
    writer.write("]}");
    last = EVENT.END;
  }

  @Override
  protected void flush() throws IOException {
    writer.flush();
  }
}
