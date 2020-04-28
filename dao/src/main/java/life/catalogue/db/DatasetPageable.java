package life.catalogue.db;

import life.catalogue.api.model.Page;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface DatasetPageable<T> {
  
  List<T> list(@Param("datasetKey") int datasetKey, @Param("page") Page page);
  
  int count(@Param("datasetKey") int datasetKey);

}
