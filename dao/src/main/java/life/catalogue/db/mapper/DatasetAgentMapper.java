package life.catalogue.db.mapper;

import life.catalogue.api.model.Dataset;

import java.util.List;

public interface DatasetAgentMapper {

  /**
   * @return a dataset object with just the key and all agent properties.
   */
  List<Dataset> listAgents();

  /**
   * Update all agents from the dataset object.
   */
  void updateAgents(Dataset obj);

}
