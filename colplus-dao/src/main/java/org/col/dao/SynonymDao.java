package org.col.dao;

import java.util.UUID;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.*;
import org.col.api.vocab.Origin;
import org.col.api.vocab.TaxonomicStatus;
import org.col.db.mapper.NameMapper;
import org.col.db.mapper.SynonymMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SynonymDao extends DatasetEntityDao<Synonym, SynonymMapper> {
  private static final Logger LOG = LoggerFactory.getLogger(SynonymDao.class);
  
  public SynonymDao(SqlSessionFactory factory) {
    super(false, factory, SynonymMapper.class);
  }
  
  private static String devNull(Reference r) {
    return null;
  }
  
  /**
   * @param syn             the source synonym to copy
   * @param accepted        the new accepted taxon to attach the copied synonym to
   * @param user
   * @param lookupReference
   */
  public static void copySynonym(final SqlSession session, final Synonym syn, final DatasetID accepted, int user,
                                 Function<Reference, String> lookupReference) {
    syn.setDatasetKey(accepted.getDatasetKey());
    TaxonDao.copyName(session, syn, accepted.getDatasetKey(), user, lookupReference);
    newKey(syn);
    syn.applyUser(user, true);
    syn.setOrigin(Origin.SOURCE);
    syn.setParentId(accepted.getId());
    session.getMapper(SynonymMapper.class).create(syn);
  }
  
  
  private static <T extends VerbatimEntity & DatasetEntity> T newKey(T e) {
    e.setVerbatimKey(null);
    e.setId(UUID.randomUUID().toString());
    return e;
  }
  
  /**
   * Creates a new Taxon including a name instance if no name id is already given.
   *
   * @param t
   * @param user
   * @return newly created taxon id
   */
  @Override
  public String create(Synonym t, int user) {
    t.setStatusIfNull(TaxonomicStatus.SYNONYM);
    if (!t.getStatus().isSynonym()) {
      throw new IllegalArgumentException("Synonym cannot have an accepted status");
    }

    try (SqlSession session = factory.openSession(false)) {
      final int datasetKey = t.getDatasetKey();
      Name n = t.getName();
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
        Name nExisting = nm.get(datasetKey, n.getId());
        if (nExisting == null) {
          throw new IllegalArgumentException("No name exists with ID " + n.getId() + " in dataset " + datasetKey);
        }
      }
      
      newKey(t);
      t.setOrigin(Origin.USER);
      t.applyUser(user);
      session.getMapper(SynonymMapper.class).create(t);
      
      session.commit();
      
      return t.getId();
    }
  }
  
  @Override
  protected void updateAfter(Synonym t, Synonym old, int user, SynonymMapper mapper, SqlSession session) {
    //TODO: update ES
  }
 
  @Override
  protected void deleteAfter(int datasetKey, String id, Synonym old, int user, SynonymMapper mapper, SqlSession session) {
    //TODO: update ES
  }
  
}
