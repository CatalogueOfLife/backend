package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.TypeMaterial;
import life.catalogue.db.*;

/**
 *
 */
public interface TypeMaterialMapper extends CRUD<DSID<String>, TypeMaterial>,
  DatasetProcessable<TypeMaterial>, SectorProcessable<TypeMaterial>, NameProcessable<TypeMaterial>, CopyDataset {

}
