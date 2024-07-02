package life.catalogue.matching.db;

import life.catalogue.matching.model.NameUsage;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

/**
 * MyBatis mapper for indexing related queries
 */
public interface IndexingMapper {

  Cursor<NameUsage> getAllForDataset(@Param("datasetKey") int datasetKey);

  Cursor<NameUsage> getAllWithExtensionForDataset(@Param("datasetKey") int datasetKey);
}
