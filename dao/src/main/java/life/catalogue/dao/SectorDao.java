package life.catalogue.dao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import life.catalogue.api.model.*;
import life.catalogue.api.search.SectorSearchRequest;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.db.mapper.TaxonMapper;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SectorDao extends DatasetEntityDao<Integer, Sector, SectorMapper> {
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(SectorDao.class);
  
  public SectorDao(SqlSessionFactory factory) {
    super(true, factory, SectorMapper.class);
  }
  
  public ResultPage<Sector> search(SectorSearchRequest request, Page page) {
    Page p = page == null ? new Page() : page;
    try (SqlSession session = factory.openSession()) {
      SectorMapper mapper = session.getMapper(SectorMapper.class);
      List<Sector> result = mapper.search(request, p);
      return new ResultPage<>(p, result, () -> mapper.countSearch(request));
    }
  }
  
  @Override
  public DSID<Integer> create(Sector s, int user) {
    s.applyUser(user);
    try (SqlSession session = factory.openSession(ExecutorType.SIMPLE, false)) {
      SectorMapper mapper = session.getMapper(SectorMapper.class);
  
      final DSID<String> did = s.getTargetAsDSID();
      TaxonMapper tm = session.getMapper(TaxonMapper.class);

      // check if source is a placeholder node
      parsePlaceholderRank(s);
      // reload full source and target
      Taxon subject = tm.get(s.getSubjectAsDSID());
      if (subject == null) {
        throw new IllegalArgumentException("subject ID " + s.getSubject().getId() + " not existing in dataset " + s.getSubjectDatasetKey());
      }
      s.setSubject(subject.toSimpleName());

      Taxon target  = tm.get(s.getTargetAsDSID());
      if (target == null) {
        throw new IllegalArgumentException("target ID " + s.getTarget().getId() + " not existing in catalogue " + s.getDatasetKey());
      }
      s.setTarget(target.toSimpleName());
      
      
      // creates sector key
      mapper.create(s);

      // for the UI to quickly render something we create a few direct children in the target !!!
      List<Taxon> toCopy = new ArrayList<>();
      // create direct children in catalogue
      if (Sector.Mode.ATTACH == s.getMode()) {
        // one taxon in ATTACH mode
        toCopy.add(subject);
      } else {
        // several taxa in UNION/MERGE mode
        toCopy = tm.children(s.getSubjectAsDSID(), s.getPlaceholderRank(), new Page(0, 5));
      }
  
      for (Taxon t : toCopy) {
        t.setSectorKey(s.getId());
        TaxonDao.copyTaxon(session, t, did, user, Collections.emptySet());
      }
  
      incSectorCounts(session, s, 1);
  
      session.commit();
      return s.getKey();
    }
    
  }

  @Override
  protected void updateBefore(Sector s, Sector old, int user, SectorMapper mapper, SqlSession session) {
    parsePlaceholderRank(s);
    super.updateBefore(s, old, user, mapper, session);
  }

  public static boolean parsePlaceholderRank(Sector s){
    RankID subjId = RankID.parseID(s.getSubjectDatasetKey(), s.getSubject().getId());
    if (subjId.rank != null) {
      s.setPlaceholderRank(subjId.rank);
      s.getSubject().setId(subjId.getId());
      return true;
    }
    return false;
  }

  @Override
  protected void updateAfter(Sector obj, Sector old, int user, SectorMapper mapper, SqlSession session) {
    if (old.getTarget() == null || obj.getTarget() == null || !Objects.equals(old.getTarget().getId(), obj.getTarget().getId())) {
      incSectorCounts(session, obj, 1);
      incSectorCounts(session, old, -1);
    }
  }
  
  @Override
  protected void deleteAfter(DSID<Integer> key, Sector old, int user, SectorMapper mapper, SqlSession session) {
    incSectorCounts(session, old, -1);
  }
  
  public static void incSectorCounts(SqlSession session, Sector s, int delta) {
    if (s != null && s.getTarget() != null) {
      TaxonMapper tm = session.getMapper(TaxonMapper.class);
      tm.incDatasetSectorCount(s.getTargetAsDSID(), s.getSubjectDatasetKey(), delta);
    }
  }

}
