package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.TaxonMetrics;

import org.apache.ibatis.annotations.Param;

public interface TaxonMetricsMapper {

  TaxonMetrics get(@Param("key") DSID<String> taxonID);

  void create(@Param("obj") TaxonMetrics metrics);

  int countByDataset(@Param("datasetKey") int datasetKey);
  int deleteByDataset(@Param("datasetKey") int datasetKey);
}