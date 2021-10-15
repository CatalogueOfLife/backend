package life.catalogue.db.tree;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Sector;
import life.catalogue.dao.TaxonCounter;

import life.catalogue.db.mapper.SectorMapper;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import org.gbif.nameparser.api.Rank;

import javax.annotation.Nullable;

import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class PrinterFactory {

  public static <T extends AbstractTreePrinter> T dataset(Class<T> clazz, int datasetKey, SqlSessionFactory factory, Writer writer) {
    return printer(clazz, datasetKey, null, null, true, null, null, null, factory, writer);
  }

  public static <T extends AbstractTreePrinter> T dataset(Class<T> clazz, int datasetKey, @Nullable String startID, boolean synonyms, Set<Rank> ranks, @Nullable Rank countRank, @Nullable TaxonCounter taxonCounter, SqlSessionFactory factory, Writer writer) {
    return printer(clazz, datasetKey, null, startID, synonyms, ranks, countRank, taxonCounter, factory, writer);
  }

  public static <T extends AbstractTreePrinter> T dataset(Class<T> clazz, int datasetKey, String startID, boolean synonyms, Rank minRank, SqlSessionFactory factory, Writer writer) {
    Set<Rank> above = null;
    if (minRank != null) {
      above = Arrays.stream(Rank.values()).filter(r -> r.ordinal() <= minRank.ordinal() || r == Rank.UNRANKED).collect(Collectors.toSet());
    }
    return printer(clazz, datasetKey, null, startID, synonyms, above, null, null, factory, writer);
  }

  /**
   * Prints a sector from the given catalogue.
   */
  public static <T extends AbstractTreePrinter> T sector(Class<T> clazz, final DSID<Integer> sectorKey, SqlSessionFactory factory, Writer writer) {
    try (SqlSession session = factory.openSession(true)) {
      Sector s = session.getMapper(SectorMapper.class).get(sectorKey);
      return printer(clazz, sectorKey.getDatasetKey(), sectorKey.getId(), s.getTarget().getId(), true, null, null, null, factory, writer);
    }
  }

  public static <T extends AbstractTreePrinter> T printer(Class<T> clazz, int datasetKey, Integer sectorKey, String startID, boolean synonyms, Set<Rank> ranks, Rank countRank, TaxonCounter taxonCounter, SqlSessionFactory factory, Writer writer) {
    try {
      return clazz.getConstructor(int.class, Integer.class, String.class, boolean.class, Set.class, Rank.class, TaxonCounter.class, SqlSessionFactory.class, Writer.class)
                  .newInstance(datasetKey, sectorKey, startID, synonyms, ranks, countRank, taxonCounter, factory, writer);
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to construct "+ clazz.getSimpleName(), e);
    }
  }

}
