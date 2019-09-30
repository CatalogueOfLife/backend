package org.col.db.mapper;

import java.util.List;

import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.Page;
import org.col.api.model.TreeNode;
import org.gbif.nameparser.api.Rank;

/**
 *
 */
public interface TreeMapper {
  
  TreeNode get(@Param("datasetKey") int datasetKey, @Param("id") String id);
  
  List<TreeNode> root(@Param("datasetKey") int datasetKey, @Param("page") Page page);

  List<TreeNode> parents(@Param("datasetKey") int datasetKey, @Param("id") String id);
  
  List<TreeNode> children(@Param("datasetKey") int datasetKey,
                          @Param("id") String id,
                          @Nullable @Param("rank") Rank rank,
                          @Param("insertPlaceholder") boolean insertPlaceholder,
                          @Param("page") Page page);
  
  /**
   * Retuns the list of unique ranks of all children of the given parentID
   * which are above the optional rank given.
   * @param datasetKey
   * @param id parentID of the children to check
   * @param rank optional minimum rank to exclude
   * @return
   */
  List<Rank> childrenRanks(@Param("datasetKey") int datasetKey,
                           @Param("id") String id,
                           @Nullable @Param("rank") Rank rank);
}
