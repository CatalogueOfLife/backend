package life.catalogue.db.mapper.legacy;

import life.catalogue.db.mapper.legacy.model.LName;
import life.catalogue.db.mapper.legacy.model.LSpeciesName;

import java.util.List;

import org.apache.ibatis.annotations.Param;

/**
 * Warning: {@link LSpeciesName#getDistribution()} is not mapped
 */
public interface LNameMapper {

  LName get(@Param("datasetKey") int datasetKey,
            @Param("id") String id);

  LName getFull(@Param("datasetKey") int datasetKey,
                @Param("id") String id);

  int count(@Param("datasetKey") int datasetKey,
            @Param("prefix") boolean prefix,
            @Param("name") String name);

  List<LName> search(@Param("datasetKey") int datasetKey,
                     @Param("prefix") boolean prefix,
                     @Param("name") String name,
                     @Param("start") int start,
                     @Param("limit") int limit);

  List<LName> searchFull(@Param("datasetKey") int datasetKey,
                     @Param("prefix") boolean prefix,
                     @Param("name") String name,
                     @Param("start") int start,
                     @Param("limit") int limit);

}
