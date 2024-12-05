package life.catalogue.dao;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.Reference;
import life.catalogue.api.model.Synonym;
import life.catalogue.api.vocab.Origin;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.SynonymMapper;
import life.catalogue.es.NameUsageIndexService;

import java.util.List;

import jakarta.validation.Validator;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
