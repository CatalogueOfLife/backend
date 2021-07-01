package life.catalogue.db.mapper;

import life.catalogue.api.model.Page;
import life.catalogue.api.model.VernacularName;
import life.catalogue.api.search.VernacularNameUsage;
import life.catalogue.api.search.VernacularSearchRequest;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import javax.ws.rs.QueryParam;

public interface VernacularNameMapper extends TaxonExtensionMapper<VernacularName> {

  List<VernacularNameUsage> searchAll(@Param("q") String q, @Param("lang") String lang, @Param("page") Page page);

  List<VernacularNameUsage> search(@Param("datasetKey") int datasetKey, @Param("req") VernacularSearchRequest request, @Param("page") Page page);

  int count(@Param("datasetKey") int datasetKey, @Param("req") VernacularSearchRequest request);

}
