package life.catalogue.db.tree;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.*;
import life.catalogue.api.txtree.Tree;
import life.catalogue.api.txtree.TreeNode;
import life.catalogue.api.vocab.Origin;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.api.vocab.Users;
import life.catalogue.dao.SectorDao;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.mapper.*;
import life.catalogue.parser.NameParser;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.nameparser.api.Rank;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A junit test rule that creates sectors and fakes a sector sync
 * by copying over all usages and setting the sectorKey.
 */
public class SectorDataRule extends ExternalResource implements AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(SectorDataRule.class);

  private SqlSession session;
  private final Supplier<SqlSessionFactory> sqlSessionFactorySupplier;
  private SectorMapper sm;
  private NameMapper nm;
  private TaxonMapper tm;
  private SynonymMapper sym;
  private NameUsageMapper um;

  private List<Sector> sectors;
  private Sector sector;
  private String parentID;

  public SectorDataRule(List<Sector> sectors) {
    this.sectors = sectors;
    sqlSessionFactorySupplier = PgSetupRule::getSqlSessionFactory;
  }

  public static Sector create(Sector.Mode mode, DSID<String> subject, DSID<String> target){
    Sector s = SectorMapperTest.create();
    s.setMode(mode);

    s.setSubjectDatasetKey(subject.getDatasetKey());
    s.getSubject().setId(subject.getId());

    s.setDatasetKey(target.getDatasetKey());
    s.getTarget().setId(target.getId());

    return s;
  }

  /**
   * @return the actual sectorKey for the supplied sector identified by the list index (starts with 0) of the constructor args.
   */
  public int sectorKey(int listIndex){
    return sectors.get(listIndex).getKey();
  }

  @Override
  protected void before() throws Throwable {
    System.out.println("Create and sync " + sectors.size() + " sectors");
    super.before();
    initSession();
    for (Sector s : sectors) {
      sector = s;
      // make sure we deal with placeholder ids correctly
      SectorDao.parsePlaceholderRank(s);
      LOG.info("Create and sync sector {}", s);
      s.applyUser(Users.TESTER);
      sm.create(s);
      // simple sync, no decisions, no extension data
      LOG.info("{} taxon tree {} to {}", s.getMode(), s.getSubject(), s.getTarget());
      if (s.getMode() == Sector.Mode.ATTACH) {
        // the parentID to be used by the copied root usage
        parentID = s.getTarget().getId();
        um.processTree(s.getSubjectDatasetKey(), null, s.getSubject().getId(), null, null, true,false)
          .forEach(this::copy);

      } else if (s.getMode() == Sector.Mode.UNION) {
        LOG.info("Traverse taxon tree at {}, ignoring immediate children above rank {}", s.getSubject().getId(), s.getPlaceholderRank());
        // in UNION mode do not attach the subject itself, just its children
        // if we have a placeholder rank configured ignore children of that rank or higher
        // see https://github.com/CatalogueOfLife/clearinghouse-ui/issues/518
        for (NameUsageBase child : um.children(DSID.key(s.getSubjectDatasetKey(), s.getSubject().getId()), s.getPlaceholderRank())){
          parentID = s.getTarget().getId();
          if (child.isSynonym()) {
            copy(child);
          } else {
            LOG.info("Traverse child {}", child);
            um.processTree(s.getSubjectDatasetKey(), null, child.getId(), null, null, true,false)
              .forEach(this::copy);
          }
        }
      }
    }
    session.commit();
  }

  private void copy(NameUsageBase u) {
    Name n = u.getName();
    n.setDatasetKey(sector.getDatasetKey());
    n.setVerbatimKey(null);
    n.setSectorKey(sector.getKey());
    n.setId(scopedSubjectID(n.getId()));
    n.setHomotypicNameId(null);
    n.setPublishedInId(null);
    n.applyUser(Users.TESTER);
    nm.create(n);

    u.setDatasetKey(sector.getDatasetKey());
    u.setVerbatimKey(null);
    u.setSectorKey(sector.getKey());
    u.setId(scopedSubjectID(u.getId()));
    if (parentID != null) {
      // this is the root usage, use the provided id
      u.setParentId(parentID);
      parentID = null;
    } else {
      u.setParentId(scopedSubjectID(u.getParentId()));
    }
    u.applyUser(Users.TESTER);
    if (u instanceof Taxon) {
      tm.create( (Taxon) u);
    } else {
      sym.create( (Synonym) u);
    }
  }

  private String scopedSubjectID(String id) {
    return sector.getSubjectDatasetKey()+":"+id;
  }

  private Sector createSector(DSID<String> subject, DSID<String> target){
    Sector s = SectorMapperTest.create();

    s.setSubjectDatasetKey(subject.getDatasetKey());
    s.getSubject().setId(subject.getId());

    s.setDatasetKey(target.getDatasetKey());
    s.getTarget().setId(target.getId());
    return s;
  }

  @Override
  protected void after() {
    super.after();
    session.close();
  }

  @Override
  public void close() {
    after();
  }

  public void initSession() {
    if (session == null) {
      session = sqlSessionFactorySupplier.get().openSession(false);
      sm = session.getMapper(SectorMapper.class);
      nm = session.getMapper(NameMapper.class);
      tm = session.getMapper(TaxonMapper.class);
      sym = session.getMapper(SynonymMapper.class);
      um = session.getMapper(NameUsageMapper.class);
    }
  }

}
