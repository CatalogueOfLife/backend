package life.catalogue.dao;

import com.github.benmanes.caffeine.cache.LoadingCache;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

import life.catalogue.api.exception.ArchivedException;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.exception.SynonymException;
import life.catalogue.api.model.*;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.api.vocab.*;
import life.catalogue.db.*;
import life.catalogue.db.mapper.*;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.parser.NameParser;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import org.gbif.nameparser.api.NameType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.validation.Validator;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

abstract class NameUsageDao<T extends NameUsageBase, M extends CRUD<DSID<String>, T> & DatasetPageable<T> & DatasetProcessable<T>> extends DatasetEntityDao<String, T, M> {
  protected final NameUsageIndexService indexService;
  protected final NameDao nameDao;

  /**
   * Warn: you must set a sector dao manually before using the TaxonDao.
   * We have circular dependency that cannot be satisfied with final properties through constructors
   */
  public NameUsageDao(Class<T> clazz, Class<M> mapperClass, SqlSessionFactory factory, NameDao nameDao, NameUsageIndexService indexService, Validator validator) {
    super(true, factory, clazz, mapperClass, validator);
    this.indexService = indexService;
    this.nameDao = nameDao;
  }

  public VerbatimSource getSource(final DSID<String> key) {
    try (SqlSession session = factory.openSession(false)) {
      return session.getMapper(VerbatimSourceMapper.class).getWithSources(key);
    }
  }

  /**
   * Creates a new usage including a name instance if no name id is already given.
   *
   * @param t
   * @param user
   * @return newly created taxon id
   */
  @Override
  public DSID<String> create(T t, int user) {
    return create(t, user, true);
  }

  /**
   * Creates a new Taxon including a name instance if no name id is already given.
   * If desired the search index is updated too.
   * @param u
   * @param user
   * @param indexImmediately if true the search index is also updated
   * @return newly created taxon id
   */
  public DSID<String> create(T u, int user, boolean indexImmediately) {
    try (SqlSession session = factory.openSession(false)) {
      final int datasetKey = u.getDatasetKey();
      Name n = u.getName();
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
        parseName(n);
        nameDao.create(n, user); // this also adds the name match
      } else {
        Name nExisting = nameDao.get(DSID.of(datasetKey, n.getId()));
        if (nExisting == null) {
          throw new IllegalArgumentException("No name exists with ID " + n.getId() + " in dataset " + datasetKey);
        }
      }
      
      newKey(u);
      u.setOrigin(Origin.USER);
      u.applyUser(user);
      session.getMapper(mapperClass).create(u);
      
      session.commit();

      // create taxon in ES
      if (indexImmediately) {
        indexService.update(u.getDatasetKey(), List.of(u.getId()));
      }
      return u;
    }
  }
  
  static void parseName(Name n) {
    if (!n.isParsed()) {
      try {
        NameParser.PARSER.parse(n, VerbatimRecord.VOID);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt(); // reset flag
      }

    } else {
      if (n.getType() == null) {
        n.setType(NameType.SCIENTIFIC);
      }
      n.rebuildScientificName();
      if (n.getAuthorship() == null) {
        n.rebuildAuthorship();
      }
    }
  }

  @Override
  protected void updateBefore(T obj, T old, int user, M mapper, SqlSession session) {
    // only allow parent changes if they are not part of a sector
    if (!Objects.equals(old.getParentId(), obj.getParentId()) && old.getSectorKey() != null) {
      throw new IllegalArgumentException("You cannot move a taxon or synonym which is part of sector " + obj.getSectorKey());
    }
  }

  @Override
  protected boolean updateAfter(T t, T old, int user, M mapper, SqlSession session, boolean keepSessionOpen) {
    session.commit();
    if (!keepSessionOpen) {
      session.close();
    }
    // update single taxon in ES
    indexService.update(t.getDatasetKey(), List.of(t.getId()));
    return keepSessionOpen;
  }

  @Override
  protected boolean deleteAfter(DSID<String> did, T old, int user, M mapper, SqlSession session) {
    NameUsageWrapper bare = old == null ? null : session.getMapper(NameUsageWrapperMapper.class).getBareName(did.getDatasetKey(), old.getName().getId());
    session.close();
    // update ES. there is probably a bare name now to be indexed!
    indexService.delete(did);
    if (bare != null) {
      indexService.add(List.of(bare));
    }
    return false;
  }
}
