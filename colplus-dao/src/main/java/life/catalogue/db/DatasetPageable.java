package life.catalogue.db;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import life.catalogue.api.model.Page;

public interface DatasetPageable<T> {
  
  List<T> list(@Param("datasetKey") int datasetKey, @Param("page") Page page);
  
  int count(@Param("datasetKey") int datasetKey);
  
}
