package org.col.db.mapper;

import java.util.List;
import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.ColUser;
import org.col.api.model.NameUsage;
import org.col.api.model.Page;
import org.gbif.nameparser.api.Rank;

/**
 * Mapper dealing with methods returning the NameUsage interface, i.e. a name in the context of either a Taxon, TaxonVernacularUsage,
 * Synonym or BareName.
 * <p>
 * Mapper sql should be reusing sql fragments from the 3 concrete implementations as much as possible avoiding duplication.
 */
public interface NameUsageMapper extends DatasetCRUDMapper<NameUsage> {
  
  int count(@Param("datasetKey") int datasetKey);
  
  List<NameUsage> list(@Param("datasetKey") int datasetKey, @Param("page") Page page);
  
  List<NameUsage> listByNameID(@Param("datasetKey") int datasetKey, @Param("nameId") String nameId);
  
  List<NameUsage> listByName(@Param("datasetKey") int datasetKey,
                         @Param("name") String sciname,
                         @Nullable @Param("rank") Rank rank);
  
  /**
   * Move all children including synonyms of a given taxon to a new parent.
   * @param datasetKey
   * @param parentId the current parentId
   * @param newParentId the new parentId
   * @return number of changed usages
   */
  int updateParentId(@Param("datasetKey") int datasetKey,
                     @Param("parentId") String parentId,
                     @Param("newParentId") String newParentId,
                     @Param("user") ColUser user);
  
  int deleteBySector(@Param("datasetKey") int datasetKey, @Param("sectorKey") int sectorKey);

}
