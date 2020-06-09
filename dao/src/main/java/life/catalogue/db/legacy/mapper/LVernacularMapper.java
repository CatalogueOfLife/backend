package life.catalogue.db.legacy.mapper;

import life.catalogue.db.legacy.model.LName;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface LVernacularMapper {

  int count(@Param("datasetKey") int datasetKey, @Param("prefix") boolean prefix, @Param("name") String name);

  List<LName> search(@Param("datasetKey") int datasetKey,
                     @Param("prefix") boolean prefix,
                     @Param("name") String name,
                     @Param("start") int start,
                     @Param("limit") int limit);
}
