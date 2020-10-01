package life.catalogue.db.tree;

import life.catalogue.api.model.NameUsageBase;
import life.catalogue.db.mapper.NameUsageMapper;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.nameparser.api.Rank;

import java.util.Set;

public abstract class NameUsageTreePrinter extends AbstractTreePrinter<NameUsageBase> {

  /**
   * @param datasetKey
   * @param sectorKey  optional sectorKey to restrict printed tree to
   * @param startID
   * @param ranks
   * @param factory
   */
  protected NameUsageTreePrinter(int datasetKey, Integer sectorKey, String startID, Set<Rank> ranks, SqlSessionFactory factory) {
    super(datasetKey, sectorKey, startID, ranks, factory);
  }

  @Override
  String getParentId(NameUsageBase usage) {
    return usage.getParentId();
  }

  @Override
  Cursor<NameUsageBase> iterate() {
    NameUsageMapper num = session.getMapper(NameUsageMapper.class);
    return num.processTree(datasetKey, sectorKey, startID, null, lowestRank, true, true);
  }

}
