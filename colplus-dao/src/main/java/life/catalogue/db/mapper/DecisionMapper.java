package life.catalogue.db.mapper;

import life.catalogue.api.model.EditorialDecision;
import life.catalogue.api.search.DecisionSearchRequest;
import life.catalogue.db.CRUD;
import life.catalogue.db.DatasetPageable;
import life.catalogue.db.Searchable;

public interface DecisionMapper extends CRUD<Integer, EditorialDecision>, DatasetPageable<EditorialDecision>,
    ProcessableDataset<EditorialDecision>, Searchable<EditorialDecision, DecisionSearchRequest> {
  
}
