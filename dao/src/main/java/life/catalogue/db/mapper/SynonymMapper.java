package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Synonym;
import life.catalogue.db.CRUD;
import life.catalogue.db.DatasetPageable;
import life.catalogue.db.DatasetProcessable;
import life.catalogue.db.TaxonProcessable;

import java.util.List;

import org.apache.ibatis.annotations.Param;

/**
 * Mapper dealing only with Synonym usages.
 * <p>
 * Mapper sql should be reusing sql fragments from the 3 concrete implementations as much as possible avoiding duplication.
 *
 * Listing by taxonID returns synonyms with the Synonym.accepted property NOT being set
 * as it would be highly redundant with the accepted key being the parameter.
 */
public interface SynonymMapper extends CRUD<DSID<String>, Synonym>, DatasetProcessable<Synonym>, DatasetPageable<Synonym>, TaxonProcessable<Synonym> {

  List<Synonym> listByNameID(@Param("datasetKey") int datasetKey, @Param("nameId") String nameId);
  
  
}
