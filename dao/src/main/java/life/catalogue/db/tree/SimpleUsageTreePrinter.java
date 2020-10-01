package life.catalogue.db.tree;

import life.catalogue.api.model.SimpleName;
import life.catalogue.db.mapper.NameUsageMapper;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.nameparser.api.Rank;

import java.util.Set;

public abstract class SimpleUsageTreePrinter extends AbstractTreePrinter<SimpleName> {
  /**
   * @param datasetKey
   * @param sectorKey  optional sectorKey to restrict printed tree to
   * @param startID
   * @param ranks
   * @param factory
   */
  protected SimpleUsageTreePrinter(int datasetKey, Integer sectorKey, String startID, Set<Rank> ranks, SqlSessionFactory factory) {
    super(datasetKey, sectorKey, startID, ranks, factory);
  }

  @Override
  String getParentId(SimpleName usage) {
    return usage.getParent();
  }

  @Override
  Cursor<SimpleName> iterate() {
    NameUsageMapper num = session.getMapper(NameUsageMapper.class);
    return num.processTreeSimple(datasetKey, sectorKey, startID, null, lowestRank, true);
  }

}
