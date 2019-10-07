package org.col.db.mapper;

import java.util.List;

import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.DSID;
import org.col.api.model.Page;
import org.col.api.model.TreeNode;
import org.gbif.nameparser.api.Rank;

/**
 *
 */
public interface TreeMapper {
  
  /**
   * @param catalogueKey the assembled catalogue the tree is related to.
   *                     The catalogueKey is always needed, even if the browsed tree is a different source dataset.
   *                     It filters which sectors & decisions should be included
   * @param key          The tree node key pointing to either a catalogue or source taxon
   */
  TreeNode get(@Param("catalogueKey") int catalogueKey,
               @Param("key") DSID<String> key);
  
  List<TreeNode> root(@Param("catalogueKey") int catalogueKey,
                      @Param("datasetKey") int datasetKey,
                      @Param("page") Page page);

  List<TreeNode> parents(@Param("catalogueKey") int catalogueKey,
                         @Param("key") DSID<String> key);
  
  List<TreeNode> children(@Param("catalogueKey") int catalogueKey,
                          @Param("key") DSID<String> key,
                          @Nullable @Param("rank") Rank rank,
                          @Param("insertPlaceholder") boolean insertPlaceholder,
                          @Param("page") Page page);
  
  /**
   * Retuns the list of unique ranks of all children of the given parentID
   * which are above the optional rank given.
   * @param rank optional minimum rank to exclude
   * @return
   */
  List<Rank> childrenRanks(@Param("key") DSID<String> key,
                           @Nullable @Param("rank") Rank rank);
}
