package life.catalogue.db.tree;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.SimpleName;
import life.catalogue.dao.TaxonCounter;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSessionFactory;

/**
 * Print an entire dataset in a flat SimpleName json array.
 */
public class JsonFlatPrinter extends AbstractTreePrinter {
  private boolean first;

  /**
   * @param sectorKey optional sectorKey to restrict printed tree to
   */
  public JsonFlatPrinter(int datasetKey, Integer sectorKey, String startID, boolean synonyms, Boolean extinct, Set<Rank> ranks, @Nullable Rank countRank, @Nullable TaxonCounter taxonCounter, SqlSessionFactory factory, Writer writer) {
    super(datasetKey, sectorKey, startID, synonyms, extinct, ranks, countRank, taxonCounter, factory, writer);
  }

  @Override
  public int print() throws IOException {
    first = true;
    writer.write("[");
    int count = super.print();
    writer.write("\n]");
    return count;
  }

  public static String countRankPropertyName(Rank rank) {
    return rank.name().toLowerCase();
  }

  protected void start(SimpleName u) throws IOException {
    u.setParent(null);
    if (first) {
      first = false;
      writer.write("\n");
    } else {
      writer.write(",\n");
    }
    String json = ApiModule.MAPPER.writeValueAsString(u);
    if (countRank == null) {
      writer.write(json);
    } else {
      writer.write(json.substring(0, json.length()-1));
      writer.write(",\"" + countRankPropertyName(countRank) + "\":" + taxonCount);
      writer.write("}");
    }
  }

  @Override
  protected void end(SimpleName u) throws IOException {
    // nothing to do
  }
}
