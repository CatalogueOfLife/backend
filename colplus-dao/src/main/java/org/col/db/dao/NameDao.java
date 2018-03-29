package org.col.db.dao;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.apache.ibatis.session.SqlSession;
import org.col.api.model.*;
import org.col.api.vocab.TaxonomicStatus;
import org.col.db.NotFoundException;
import org.col.db.mapper.NameMapper;
import org.col.db.mapper.ReferenceMapper;
import org.col.db.mapper.SynonymMapper;
import org.col.db.mapper.VerbatimRecordMapper;
import org.col.db.mapper.temp.ReferenceWithPage;
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
  }

  /**
   * Lists all homotypic basionymGroup based on the same basionym
   */
  public List<Name> basionymGroup(int key) {
    // Allow 404 to be thrown:
    // TODO: create more light-weight EXISTS mappper method to replace get().
    get(key);
    NameMapper mapper = session.getMapper(NameMapper.class);
    return mapper.basionymGroup(key);
  }

  /**
   * Adds a new synonym link for an existing taxon and synonym name. This link is used for both a
   * hetero- or homotypic synonym.
   *
   * @param synonym the synonym to create - requires and existing name with key to exist!
   */
  public void addSynonym(Synonym synonym) {
    Preconditions.checkNotNull(synonym.getName().getKey(), "Name key must exist");
    Preconditions.checkNotNull(synonym.getName().getKey(), "Name key must exist");
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
    s.getAccepted().add(t);

    return s;
  }

  /**
   * Assemble a synonymy object from the list of synonymy names for a given accepted taxon.
   */
  public Synonymy getSynonymy(int taxonKey) {
    SynonymMapper synMapper = session.getMapper(SynonymMapper.class);
    NameMapper nMapper = session.getMapper(NameMapper.class);
    Synonymy syn = new Synonymy();
    int lastBasKey = -1;
    List<Synonym> homotypics = null;
    for (Synonym s : synMapper.synonyms(taxonKey)) {
      int basKey = s.getName().getBasionymKey() == null ? s.getName().getKey() : s.getName().getBasionymKey();
      if (lastBasKey == -1 || basKey != lastBasKey) {
        lastBasKey = basKey;
        // new homotypic group
        if (homotypics != null) {
          syn.addHomotypicGroup(homotypics);
        }
        homotypics = Lists.newArrayList();
      }
      homotypics.add(s);
    }
    if (homotypics != null) {
      syn.addHomotypicGroup(homotypics);
    }
    return syn;
  }

  public ResultPage<NameUsage> search(NameSearch query, Page page) {
    if (query.isEmpty()) {
      // default to order by key for large, unfiltered resultssets
      query.setSortBy(NameSearch.SortBy.KEY);
    } else if (query.getSortBy() == null) {
      query.setSortBy(NameSearch.SortBy.NAME);
    }
    if (query.getQ() != null) {
      query.setQ(query.getQ() + ":*");
    }
    NameMapper mapper = session.getMapper(NameMapper.class);
    int total = mapper.countSearchResults(query);
    List<NameUsage> result = mapper.search(query, page);
    return new ResultPage<>(page, total, result);
  }

  public Reference getPublishedIn(int nameKey) {
    ReferenceMapper mapper = session.getMapper(ReferenceMapper.class);
    ReferenceWithPage rwp = mapper.getPublishedIn(nameKey);
    if(rwp == null) {
      return null;
    }
    return rwp.toReference();
  }

  public VerbatimRecord getVerbatim(int nameKey) {
    VerbatimRecordMapper mapper = session.getMapper(VerbatimRecordMapper.class);
    return mapper.getByName(nameKey);
  }

}
