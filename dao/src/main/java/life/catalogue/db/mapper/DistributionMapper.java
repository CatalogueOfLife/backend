package life.catalogue.db.mapper;

import life.catalogue.api.model.Distribution;

import java.util.List;

import org.apache.ibatis.annotations.Param;

public interface DistributionMapper extends TaxonExtensionMapper<Distribution> {

  /**
   * Lists the ids of all taxa with more than one threat status for the same area, ignoring NOT_EVALUATED.
   */
  List<String> listTaxonIDsWithAmbiguousThreatStatus(@Param("datasetKey") int datasetKey);
}
