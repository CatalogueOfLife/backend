package life.catalogue.db.mapper.legacy;

import life.catalogue.db.mapper.legacy.model.LName;

import java.util.List;

import org.apache.ibatis.annotations.Param;

public interface LVernacularMapper {

  int count(@Param("datasetKey") int datasetKey, @Param("prefix") boolean prefix, @Param("name") String name);

  List<LName> search(@Param("datasetKey") int datasetKey,
                     @Param("prefix") boolean prefix,
                     @Param("name") String name,
                     @Param("start") int start,
                     @Param("limit") int limit);
}
