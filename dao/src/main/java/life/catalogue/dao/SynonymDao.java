package life.catalogue.dao;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Synonym;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.db.mapper.SynonymMapper;
import life.catalogue.es.indexing.NameUsageIndexService;

import org.apache.ibatis.session.SqlSessionFactory;

import jakarta.validation.Validator;

public class SynonymDao extends NameUsageDao<Synonym, SynonymMapper> {
  public SynonymDao(SqlSessionFactory factory, NameDao nameDao, NameUsageIndexService indexService, Validator validator) {
    super(Synonym.class, SynonymMapper.class, factory, nameDao, indexService, validator);
  }

  /**
   * Creates a new Taxon including a name instance if no name id is already given.
   *
   * @param syn
   * @param user
   * @param indexImmediately if true the search index is also updated
   * @return newly created taxon id
   */
  public DSID create(Synonym syn, int user, boolean indexImmediately) {
    syn.setStatusIfNull(TaxonomicStatus.SYNONYM);
    if (!syn.getStatus().isSynonym()) {
      throw new IllegalArgumentException("Synonym cannot have an accepted status");
    }
    return super.create(syn, user, indexImmediately);
  }
  
}
