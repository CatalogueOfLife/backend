package org.col.db.dao;

import com.google.common.base.Preconditions;
import org.apache.ibatis.session.SqlSession;
import org.col.api.model.*;
import org.col.api.vocab.TaxonomicStatus;
import org.col.db.NotFoundException;
import org.col.db.mapper.NameMapper;
import org.col.db.mapper.SynonymMapper;
import org.col.db.mapper.VerbatimRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class NameDao {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameDao.class);

  private final SqlSession session;

  public NameDao(SqlSession sqlSession) {
    this.session = sqlSession;
  }

  public int count(int datasetKey) {
    NameMapper mapper = session.getMapper(NameMapper.class);
    return mapper.count(datasetKey);
  }

  public ResultPage<Name> list(Integer datasetKey, Page page) {
    NameMapper mapper = session.getMapper(NameMapper.class);
    int total = mapper.count(datasetKey);
    List<Name> result = mapper.list(datasetKey, page);
    return new ResultPage<>(page, total, result);
  }

  public Integer lookupKey(String id, int datasetKey) throws NotFoundException {
    NameMapper mapper = session.getMapper(NameMapper.class);
    Integer key = mapper.lookupKey(id, datasetKey);
    if (key == null) {
      throw NotFoundException.idNotFound(Name.class, datasetKey, id);
    }
    return key;
  }

  public Name get(Integer key) {
    NameMapper mapper = session.getMapper(NameMapper.class);
    Name result = mapper.get(key);
    if (result == null) {
      throw NotFoundException.keyNotFound(Name.class, key);
    }
    return result;
  }

  public Name get(String id, int datasetKey) {
    return get(lookupKey(id, datasetKey));
  }

  public void create(Name name) {
    NameMapper mapper = session.getMapper(NameMapper.class);
    mapper.create(name);
    // this happens in the db but is not cascaded to the instance by the mapper
    // to avoid a reload of the instance from the db we do this manually here
    if (name.getHomotypicNameKey() == null) {
      name.setHomotypicNameKey(name.getKey());
    }
  }

  /**
   * Lists all homotypic synonyms based on the same homotypic group key
   */
  public List<Name> homotypicGroup(int key) {
    NameMapper mapper = session.getMapper(NameMapper.class);
    return mapper.homotypicGroup(key);
  }

  /**
   * Adds a new synonym link for an existing taxon and synonym name,
   * which is a heterotypic synonym or misapplied name.
   *
   * Only names which represent a homotypic group should be added with status=SYNONYM.
   *
   * @param synonym the synonym to create - requires and existing name with key to exist!
   */
  public void addSynonym(Synonym synonym) {
    Preconditions.checkNotNull(synonym.getName().getKey(), "Name key must exist");
    if (TaxonomicStatus.MISAPPLIED != synonym.getStatus()) {
      Preconditions.checkArgument(synonym.getName().getHomotypicNameKey() == null || synonym.getName().getKey().equals(synonym.getName().getHomotypicNameKey()), "Name must be representing a homotypic group");
    }
    SynonymMapper mapper = session.getMapper(SynonymMapper.class);
    mapper.create(synonym);
  }

  public static Synonym toSynonym(int datasetKey, int accKey, int nameKey) {
    Synonym s = new Synonym();
    s.setStatus(TaxonomicStatus.SYNONYM);

    Name n = new Name();
    n.setKey(nameKey);
    n.setDatasetKey(datasetKey);
    s.setName(n);

    Taxon t = new Taxon();
    t.setKey(accKey);
    t.setDatasetKey(datasetKey);
    s.setAccepted(t);

    return s;
  }

  /**
   * Assemble a synonymy object from the list of synonymy names for a given accepted taxon.
   */
  public Synonymy getSynonymy(int taxonKey) {
    SynonymMapper synMapper = session.getMapper(SynonymMapper.class);
    NameMapper nMapper = session.getMapper(NameMapper.class);
    Synonymy syn = new Synonymy();
    // get homotypic synonyms for the accepted name
    syn.getHomotypic().addAll(nMapper.homotypicGroupByTaxon(taxonKey));
    // get all heterotypic synonyms and misapplied names
    for (Synonym s : synMapper.synonyms(taxonKey)) {
      if (TaxonomicStatus.MISAPPLIED == s.getStatus()) {
        syn.addMisapplied(new NameAccordingTo(s.getName(), s.getAccordingTo()));
      } else {
        // get entire homotypic group
        syn.addHeterotypicGroup(nMapper.homotypicGroup(s.getName().getKey()));
      }
    }
    return syn;
  }

  public VerbatimRecord getVerbatim(int nameKey) {
    VerbatimRecordMapper mapper = session.getMapper(VerbatimRecordMapper.class);
    return mapper.getByName(nameKey);
  }

}
