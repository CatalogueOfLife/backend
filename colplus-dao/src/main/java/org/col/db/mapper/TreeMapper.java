package org.col.db.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.col.api.model.Page;
import org.col.api.model.TreeNode;

/**
 *
 */
public interface TreeMapper {
  
  List<TreeNode.TreeNodeMybatis> root(@Param("datasetKey") int datasetKey, @Param("page") Page page);

  List<TreeNode.TreeNodeMybatis> parents(@Param("datasetKey") int datasetKey, @Param("id") String id);

  List<TreeNode.TreeNodeMybatis> children(@Param("datasetKey") int datasetKey, @Param("id") String id, @Param("page") Page page);

}
