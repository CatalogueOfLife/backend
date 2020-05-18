package life.catalogue.release;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.db.mapper.*;
import life.catalogue.es.NameUsageIndexService;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * Job to duplicate a managed project with all its data, decisions and metadata
 */
public class ProjectDuplication extends ProjectRunnable {

  private Int2IntMap sectors;

  ProjectDuplication(SqlSessionFactory factory, NameUsageIndexService indexService, DatasetImportDao diDao, int datasetKey, Dataset copy, int userKey) {
    super("duplicating", factory, diDao, indexService, userKey, datasetKey, copy);
  }

  @Override
  void dataWork() throws Exception {
    LOG.info("Duplicate project "+ datasetKey +" to new dataset" + getNewDatasetKey());
    // copy data
    updateState(ImportState.INSERTING);

    sectors = copyTableWithKeyMap(SectorMapper.class, Sector.class, this::updateEntity);
    copyTable(DecisionMapper.class, EditorialDecision.class, this::updateEntity);
    copyTable(EstimateMapper.class, SpeciesEstimate.class, this::updateEntity);

    copyTable(VerbatimRecordMapper.class, VerbatimRecord.class, this::updateEntity);

    copyTable(ReferenceMapper.class, Reference.class, this::updateSectorEntity);
    copyTable(NameMapper.class, Name.class, this::updateSectorEntity);
    copyTable(TaxonMapper.class, Taxon.class, this::updateSectorEntity);
    copyTable(SynonymMapper.class, Synonym.class, this::updateSectorEntity);
    copyTable(TypeMaterialMapper.class, TypeMaterial.class, this::updateSectorEntity);

    copyTable(NameRelationMapper.class, NameRelation.class, this::updateEntity);

    copyExtTable(VernacularNameMapper.class, VernacularName.class, this::updateExtensionEntity);
    copyExtTable(DistributionMapper.class, Distribution.class, this::updateExtensionEntity);
    copyExtTable(DescriptionMapper.class, Description.class, this::updateExtensionEntity);
    copyExtTable(MediaMapper.class, Media.class, this::updateExtensionEntity);
  }

  private <T extends DSID<?>> void updateEntity(T obj) {
    obj.setId(null);
    obj.setDatasetKey(newDatasetKey);
  }
  private <C extends DSID<?> & SectorEntity> void updateSectorEntity(C obj) {
    obj.setDatasetKey(newDatasetKey);
    if (obj.getSectorKey() != null) {
      obj.setSectorKey(sectors.get((int)obj.getSectorKey()));
    }
  }
  private <E extends DatasetScopedEntity<Integer> & VerbatimEntity> void updateExtensionEntity(TaxonExtension<E> obj) {
    updateEntity(obj.getObj());
  }

}
