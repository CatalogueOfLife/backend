package life.catalogue.db;

import java.util.List;

import org.apache.ibatis.annotations.Param;

/**
 * Fetches the entities of many names at once, the name scoped counterpart of {@link TaxonBatchable}.
 *
 * @param <T> entity type
 */
public interface NameBatchable<T> {

  /**
   * @param ids name ids to fetch the entities of. Must not be empty.
   */
  List<T> listByNames(@Param("datasetKey") int datasetKey, @Param("ids") List<String> ids);
}
