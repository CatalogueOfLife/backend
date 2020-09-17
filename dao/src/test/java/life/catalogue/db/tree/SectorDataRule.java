package life.catalogue.db.tree;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.Users;
import life.catalogue.dao.SectorDao;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.mapper.*;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Supplier;

/**
 * A junit test rule that creates sectors and fakes a sector sync
 * by copying over all name usages and setting the sectorKey.
 * It is much quicker than a regular sync, but ignores decisions and does not copy references, vernacular names, etc.
 * It offers a way to set the extinct flag for all usages from a given sector if needed.
 *
 * The rule was designed to run as a junit {@link org.junit.Rule} before every test or test class if you only need to test reads.
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
  private Boolean extinct;
  private Sector sector;
  private String parentID;

  /**
   * @param sectors list of sectors to create and sync
   */
  public SectorDataRule(List<Sector> sectors) {
    this.sectors = sectors;
    sqlSessionFactorySupplier = PgSetupRule::getSqlSessionFactory;
  }

  public static Sector create(Sector.Mode mode, DSID<String> subject, DSID<String> target){
    return create(mode, subject, target, null);
  }

  /**
   * Utility method to build sectors to be passed into the constructor.
   * The sectors are not persisted.
   * @param extinct if given the extinct flag for all copied usages from the sector is set
   */
  public static Sector create(Sector.Mode mode, DSID<String> subject, DSID<String> target, Boolean extinct){
    Sector s = new Sector();
    s.setMode(mode);

    s.setSubjectDatasetKey(subject.getDatasetKey());
    s.setSubject(SimpleName.of(subject));

    s.setDatasetKey(target.getDatasetKey());
    s.setTarget(SimpleName.of(target));

    if (extinct != null) {
      s.setNote(extinct.toString());
    }
    return s;
  }

  /**
   * @return the actual sectorKey for the supplied sector identified by the list index (starts with 0) of the constructor args.
   */
  public int sectorKey(int listIndex){
    return sectors.get(listIndex).getId();
  }

  @Override
  protected void before() throws Throwable {
    System.out.println("Create and sync " + sectors.size() + " sectors");
    super.before();
    initSession();
    for (Sector s : sectors) {
      sector = s;
      // set extinct?
      if (s.getNote() != null) {
        extinct = Boolean.parseBoolean(s.getNote());
      } else {
        extinct = null;
      }
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
        for (NameUsageBase child : um.children(DSID.of(s.getSubjectDatasetKey(), s.getSubject().getId()), s.getPlaceholderRank())){
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
    n.setSectorKey(sector.getId());
    n.setId(scopedSubjectID(n.getId()));
    n.setHomotypicNameId(null);
    n.setPublishedInId(null);
    n.applyUser(Users.TESTER);
    nm.create(n);

    u.setDatasetKey(sector.getDatasetKey());
    u.setVerbatimKey(null);
    u.setSectorKey(sector.getId());
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
      if (extinct != null) {
        ((Taxon) u).setExtinct(extinct);
      }
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
