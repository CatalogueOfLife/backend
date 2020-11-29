package life.catalogue.db.mapper;

import life.catalogue.api.model.NameRelation;
import life.catalogue.api.vocab.NomRelType;
import life.catalogue.db.CopyDataset;
import life.catalogue.db.Create;
import life.catalogue.db.DatasetProcessable;
import life.catalogue.db.SectorProcessable;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface NameRelationMapper extends Create<NameRelation>,
                                            DatasetProcessable<NameRelation>,
                                            SectorProcessable<NameRelation>,
                                            CopyDataset {
  
  /**
   * Returns the list of name relations for a single name,
   * regardless which side of the act relation the name is on.
   */
  List<NameRelation> list(@Param("datasetKey") int datasetKey, @Param("nameId") String nameId);
  
  /**
   * Returns the list of related names of a given type for a single name on the nameId side of the relation only.
   */
  List<NameRelation> listByType(@Param("datasetKey") int datasetKey, @Param("nameId") String nameId, @Param("type") NomRelType type);

  /**
   * Returns the list of related names of a given type for a single name on the relatedNameId side of the relation only.
   */
  List<NameRelation> listByTypeReverse(@Param("datasetKey") int datasetKey, @Param("relatedNameId") String relatedNameId, @Param("type") NomRelType type);
}
