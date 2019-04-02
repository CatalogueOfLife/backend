package org.col.db.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.Page;
import org.col.api.model.TreeNode;

/**
 *
 */
public interface TreeMapper {
  
  List<TreeNode> root(@Param("datasetKey") int datasetKey, @Param("page") Page page);

  List<TreeNode> parents(@Param("datasetKey") int datasetKey, @Param("id") String id);

  List<TreeNode> children(@Param("datasetKey") int datasetKey, @Param("id") String id, @Param("page") Page page);

}
