package org.col.db.mapper;

import java.util.List;
import java.util.Set;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.ResultHandler;
import org.col.api.model.Page;
import org.col.api.model.Taxon;

/**
 * Taxon mapper with CRUD features.
 *
 * For create or update note that the name must exist already and taxon.name.id be present.
 */
public interface TaxonMapper extends DatasetCRUDMapper<Taxon> {
  
  int countRoot(@Param("datasetKey") int datasetKey);
  
  List<Taxon> listRoot(@Param("datasetKey") int datasetKey, @Param("page") Page page);
  
  List<Taxon> listByName(@Param("datasetKey") int datasetKey, @Param("nameId") String nameId);
  
  /**
   * @return list of all parents starting with the immediate parent
   */
  List<Taxon> classification(@Param("datasetKey") int datasetKey, @Param("id") String id);
  
  int countChildren(@Param("datasetKey") int datasetKey, @Param("id") String id);
  
  List<Taxon> children(@Param("datasetKey") int datasetKey, @Param("id") String id, @Param("page") Page page);
  
  /**
   * Iterates over all accepted descendants in a tree in depth first order for a given start/root taxon
   * and processes them with the supplied handler. This allows a single
   * query to efficiently stream all its values without keeping them in memory.
   *
   * An optional exclusion filter can be used to prevent traversal of subtrees.
   * Synonyms are not traversed, this only works on Taxa!
   *
   * @param startID taxon id to start the traversal. Will be included in the result
   * @param exclusions set of taxon ids to exclude from traversal. This will also exclude all descendants
   */
  void processTree(@Param("datasetKey") int datasetKey,
                   @Param("startID") String startID,
                   @Param("exclusions") Set<String> exclusions,
                   ResultHandler<Taxon> handler);
  
}
