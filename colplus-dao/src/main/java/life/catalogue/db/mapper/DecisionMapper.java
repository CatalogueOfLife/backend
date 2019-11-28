package life.catalogue.db.mapper;

import java.util.List;

import life.catalogue.api.model.EditorialDecision;
import life.catalogue.api.model.Page;
import life.catalogue.api.search.DecisionSearchRequest;
import life.catalogue.db.CRUD;
import life.catalogue.db.DatasetPageable;
import org.apache.ibatis.annotations.Param;

public interface DecisionMapper extends CRUD<Integer, EditorialDecision>, DatasetPageable<EditorialDecision>, ProcessableDataset<EditorialDecision> {
  
  List<EditorialDecision> search(@Param("req") DecisionSearchRequest request, @Param("page") Page page);
  
  int countSearch(@Param("req") DecisionSearchRequest request);
  
}
