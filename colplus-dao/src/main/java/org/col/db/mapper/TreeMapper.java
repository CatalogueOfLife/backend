package org.col.db.mapper;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.TreeNode;

import java.util.List;

/**
 *
 */
public interface TreeMapper {

  //TreeNode get(@Param("datasetKey") int datasetKey, @Param("id") String id);

  List<TreeNode> root(@Param("datasetKey") int datasetKey);

  List<TreeNode> parents(@Param("datasetKey") int datasetKey, @Param("id") String id);

  List<TreeNode> children(@Param("datasetKey") int datasetKey, @Param("id") String id);

}
