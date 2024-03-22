package life.catalogue.matching;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

public interface IndexingMapper {

  Cursor<NameUsage> getAllForDataset(@Param("datasetKey") int datasetKey);
}
