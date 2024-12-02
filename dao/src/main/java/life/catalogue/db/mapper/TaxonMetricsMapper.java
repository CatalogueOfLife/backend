package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Publisher;
import life.catalogue.api.model.TaxonMetrics;
import life.catalogue.api.model.Treatment;
import life.catalogue.db.*;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

public interface TaxonMetricsMapper {

  TaxonMetrics get(@Param("key") DSID<String> taxonID);

  void create(@Param("obj") TaxonMetrics metrics);

}