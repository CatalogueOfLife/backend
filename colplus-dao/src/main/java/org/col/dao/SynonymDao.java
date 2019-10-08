package org.col.dao;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.DSID;
import org.col.api.model.Name;
import org.col.api.model.Reference;
import org.col.api.model.Synonym;
import org.col.api.vocab.Origin;
import org.col.api.vocab.TaxonomicStatus;
import org.col.db.mapper.NameMapper;
import org.col.db.mapper.SynonymMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SynonymDao extends DatasetEntityDao<String, Synonym, SynonymMapper> {
  private static final Logger LOG = LoggerFactory.getLogger(SynonymDao.class);
  
  public SynonymDao(SqlSessionFactory factory) {
    super(false, factory, SynonymMapper.class);
  }
  
  private static String devNull(Reference r) {
    return null;
  }
  
  /**
   * Creates a new Taxon including a name instance if no name id is already given.
   *
   * @param syn
   * @param user
   * @return newly created taxon id
   */
  @Override
  public DSID create(Synonym syn, int user) {
    syn.setStatusIfNull(TaxonomicStatus.SYNONYM);
    if (!syn.getStatus().isSynonym()) {
      throw new IllegalArgumentException("Synonym cannot have an accepted status");
    }

    try (SqlSession session = factory.openSession(false)) {
      final int datasetKey = syn.getDatasetKey();
      Name n = syn.getName();
      NameMapper nm = session.getMapper(NameMapper.class);
      if (n.getId() == null) {
        if (!n.isParsed() && StringUtils.isBlank(n.getScientificName())) {
          throw new IllegalArgumentException("Existing nameId, scientificName or atomized name field required");
        }
        newKey(n);
        n.setOrigin(Origin.USER);
        n.applyUser(user);
        // make sure we use the same dataset
        n.setDatasetKey(datasetKey);
        // does the name need parsing?
        TaxonDao.parseName(n);
        nm.create(n);
      } else {
        Name nExisting = nm.get(n);
        if (nExisting == null) {
          throw new IllegalArgumentException("No name exists with ID " + n.getId() + " in dataset " + datasetKey);
        }
      }
      
      newKey(syn);
      syn.setOrigin(Origin.USER);
      syn.applyUser(user);
      session.getMapper(SynonymMapper.class).create(syn);
      
      session.commit();
      
      return syn;
    }
  }
  
  @Override
  protected void updateAfter(Synonym t, Synonym old, int user, SynonymMapper mapper, SqlSession session) {
    //TODO: update ES
  }
 
  @Override
  protected void deleteAfter(DSID id, Synonym old, int user, SynonymMapper mapper, SqlSession session) {
    //TODO: update ES
  }
  
}
