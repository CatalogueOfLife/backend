package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.TreeNode;

import org.gbif.nameparser.api.Rank;

import java.util.List;

import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;

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
               @Nullable @Param("type") TreeNode.Type type,
               @Param("key") DSID<String> key);

  List<TreeNode> classification(@Param("catalogueKey") int catalogueKey,
                                @Param("type") TreeNode.Type type,
                                @Param("key") DSID<String> key);

  /**
   * Lists children or root taxa if the key id is just a datasetKey
   * @param key the ID of the parent to list children or a DSID without an id to list the root taxa of a given dataset
   */
  List<TreeNode> children(@Param("catalogueKey") int catalogueKey,
                          @Nullable @Param("type") TreeNode.Type type,
                          @Param("key") DSID<String> key,
                          @Param("extinct") boolean inclExtinct,
                          @Param("page") Page page);

  /**
   * Lists children or root taxa if the key id is just a datasetKey
   * @param key the ID of the parent to list children or a DSID without an id to list the root taxa of a given dataset
   * @param rank the rank. Use NULL for root taxa
   */
  List<TreeNode> childrenWithPlaceholder(@Param("catalogueKey") int catalogueKey,
                                         @Nullable @Param("type") TreeNode.Type type,
                                         @Param("key") DSID<String> key,
                                         @Nullable @Param("rank") Rank rank,
                                         @Param("extinct") boolean inclExtinct,
                                         @Param("page") Page page);

  /**
   * Returns the list of unique ranks of all accepted children of the given parentID
   * which are above or equal the optional rank given.
   * @param rank optional minimum rank to consider
   * @return
   */
  List<Rank> childrenRanks(@Param("key") DSID<String> key,
                           @Nullable @Param("rank") Rank rank,
                           @Param("extinct") boolean inclExtinct);

  /**
   * Returns the list of unique sectors of all children of the given parentID
   * which are below the optional rank given.
   * Null values are also included if at least one child has no sectorKey
   *
   * @param rank optional rank threshold
   */
  List<Integer> childrenSectors(@Param("key") DSID<String> key,
                                @Nullable @Param("rank") Rank rank);

}
