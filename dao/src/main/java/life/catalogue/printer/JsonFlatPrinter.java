package life.catalogue.printer;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.TreeTraversalParameter;
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
public class JsonFlatPrinter extends AbstractPrinter {
  private boolean first;

  public JsonFlatPrinter(TreeTraversalParameter params, Set<Rank> ranks, @Nullable Boolean extinct, @Nullable Rank countRank, @Nullable TaxonCounter taxonCounter, SqlSessionFactory factory, Writer writer) {
    super(false, params, ranks, extinct, countRank, taxonCounter, factory, writer);
  }

  @Override
  public int print() throws IOException {
    first = true;
    writer.write("[");
    int count = super.print();
    writer.write("\n]");
    return count;
  }

  private static String countRankPropertyName(Rank rank) {
    return rank.name().toLowerCase();
  }

  @Override
  public void print(SimpleName u) {
    try {
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
    } catch (IOException e) {
      throw new PrinterException(e);
    }
  }

}
