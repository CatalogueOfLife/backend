package life.catalogue.printer;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Sector;
import life.catalogue.api.model.TreeTraversalParameter;
import life.catalogue.dao.TaxonCounter;
import life.catalogue.db.mapper.SectorMapper;

import org.gbif.nameparser.api.Rank;

import java.io.Writer;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import static life.catalogue.api.exception.NotFoundException.throwNotFoundIfNull;

public class PrinterFactory {

  public static <T extends AbstractPrinter> T dataset(Class<T> clazz, int datasetKey, SqlSessionFactory factory, Writer writer) {
    return printer(clazz, TreeTraversalParameter.dataset(datasetKey), null, null, null, null, factory, writer);
  }

  public static <T extends AbstractPrinter> T dataset(Class<T> clazz, TreeTraversalParameter params, SqlSessionFactory factory, Writer writer) {
    return printer(clazz, params, null, null, null, null, factory, writer);
  }

  /**
   * @param ranks can be given explicitly to select the ranks to print. If lowestRank is given in params this will be used to ignore any ranks below
   */
  public static <T extends AbstractPrinter> T dataset(Class<T> clazz, TreeTraversalParameter params,
                                                      @Nullable Set<Rank> ranks, @Nullable Boolean extinct,
                                                      @Nullable Rank countRank, @Nullable TaxonCounter taxonCounter,
                                                      SqlSessionFactory factory, Writer writer) {
    return printer(clazz, params, ranks, extinct, countRank, taxonCounter, factory, writer);
  }

  private static <T extends AbstractPrinter> T printer(Class<T> clazz, TreeTraversalParameter params,
                                                      @Nullable Set<Rank> ranks, @Nullable Boolean extinct,
                                                      @Nullable Rank countRank, @Nullable TaxonCounter taxonCounter,
                                                      SqlSessionFactory factory, Writer writer) {
    try {
      return clazz.getConstructor(TreeTraversalParameter.class, Set.class, Boolean.class, Rank.class, TaxonCounter.class, SqlSessionFactory.class, Writer.class)
                  .newInstance(params, ranks, extinct, countRank, taxonCounter, factory, writer);
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to construct "+ clazz.getSimpleName(), e);
    }
  }

}
