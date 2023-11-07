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

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * Print an entire dataset in a nested SimpleName json array.
 */
public class JsonTreePrinter extends AbstractTreePrinter {
  private static final int indentation = 2;

  public JsonTreePrinter(TreeTraversalParameter params, Set<Rank> ranks, @Nullable Rank countRank, @Nullable TaxonCounter taxonCounter, SqlSessionFactory factory, Writer writer) {
    super(params, ranks, countRank, taxonCounter, factory, writer);
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
  }

  protected void end(SimpleName u) throws IOException {
    if (last == EVENT.END) {
      writer.write("\n");
      writer.write(StringUtils.repeat(' ', (level-1) * indentation));
    }
    writer.write("]}");
  }

}
