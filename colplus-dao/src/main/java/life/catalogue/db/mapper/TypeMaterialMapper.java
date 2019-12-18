package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.TypeMaterial;
import life.catalogue.db.CRUD;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 *
 */
public interface TypeMaterialMapper extends CRUD<DSID<Integer>, TypeMaterial>, ProcessableDataset<TypeMaterial> {

	List<TypeMaterial> listByName(@Param("key") DSID<String> key);

}
