package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.TaxonExtension;
import life.catalogue.api.model.VernacularName;
import life.catalogue.api.search.VernacularNameUsage;
import life.catalogue.api.search.VernacularSearchRequest;

import java.util.List;

import org.apache.ibatis.annotations.Param;

public interface VernacularNameMapper extends TaxonExtensionMapper<VernacularName> {

  List<VernacularNameUsage> searchAll(@Param("q") String q, @Param("lang") String lang, @Param("page") Page page);

  List<VernacularNameUsage> search(@Param("datasetKey") int datasetKey, @Param("req") VernacularSearchRequest request, @Param("page") Page page);

  int count(@Param("datasetKey") int datasetKey, @Param("req") VernacularSearchRequest request);

  @Override
  default List<VernacularName> listByTaxon(@Param("key") DSID<String> key) {
    return listByTaxonFiltered(key, null);
  }

  List<VernacularName> listByTaxonFiltered(@Param("key") DSID<String> key, @Param("lang") String language);

  /**
   * Lists the vernacular names that other sectors - or editors - attached to the usages of the given sector.
   * Their foreign key would keep the sector from deleting its usages, which a sync does.
   *
   * @param key the sector key
   * @return the foreign vernacular names, each with the source id of its usage as the taxonID, not the usage id
   */
  List<TaxonExtension<VernacularName>> listForeignBySector(@Param("key") DSID<Integer> key);

  /**
   * Deletes the vernacular names listed by {@link #listForeignBySector}.
   * @return number of deleted vernacular names
   */
  int deleteForeignBySector(@Param("key") DSID<Integer> key);

}
