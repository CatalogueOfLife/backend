package life.catalogue.matching;

import life.catalogue.api.model.Name;
import life.catalogue.api.model.NameMatch;
import life.catalogue.db.mapper.MatchMapper;
import life.catalogue.matching.nidx.NameIndex;

import java.util.Objects;
import java.util.function.Consumer;

import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

public class BaseMatcher {
  private static final Logger LOG = LoggerFactory.getLogger(BaseMatcher.class);
  protected final SqlSessionFactory factory;
  protected final NameIndex ni;
  protected int total = 0;
  protected int updated = 0;
  protected int nomatch = 0;

  public BaseMatcher(SqlSessionFactory factory, NameIndex ni) {
    this.factory = factory;
    this.ni = ni.assertOnline();
  }

  public int getTotal() {
    return total;
  }

  public int getUpdated() {
    return updated;
  }

  public int getNomatch() {
    return nomatch;
  }

  class BulkMatchHandler implements Consumer<Name>, AutoCloseable {
    private final boolean allowInserts;
    private final boolean update;

    final SqlSession batchSession; // this is only a true batch session if we are NOT in UPDATE mode
    MatchMapper nmm;
    private int _total = 0;
    private int _updated = 0;
    private int _nomatch = 0;
    private IntSet datasets = new IntOpenHashSet();

    BulkMatchHandler(boolean allowInserts, Class<? extends MatchMapper> mapperClass, boolean update) {
      this.allowInserts = allowInserts;
      this.update = update;
      this.batchSession = update ? // in update mode we also need to read to make sure we update or insert correctly
        factory.openSession(true) :
        factory.openSession(ExecutorType.BATCH, false);
      this.nmm = batchSession.getMapper(mapperClass);
    }

    @Override
    public void accept(Name n) {
      _total++;
      final Integer oldId = n.getNamesIndexId();
      NameMatch m = ni.match(n, allowInserts, false);
      if (!m.isMatched()) {
        _nomatch++;
        // we only log here, but persist below
        LOG.debug("No match for {} from dataset {}", n.toStringComplete(), n.getDatasetKey());
      }
      if (!Objects.equals(oldId, m.getNidx())) {
        persist(n, m);
        if (_updated++ % 10000 == 0) {
          if (!update) {
            batchSession.commit();
          }
          LOG.debug("Updated {} name matches for {} names with {} no matches", _updated, _total, _nomatch);
        }
      }
    }

    void persist(Name n, NameMatch m) {
      datasets.add(n.getDatasetKey());
      if (update) {
        // we don't know upfront whether a match record already exists for this name (e.g. because it was
        // never matched before) - try to update it and fall back to inserting a new record if none was updated
        if (nmm.update(n, m.getNidx()) < 1) {
          nmm.create(n, n.getSectorKey(), m.getNidx());
        }
      } else {
        nmm.create(n, n.getSectorKey(), m.getNidx());
      }
    }

    public IntSet getDatasets() {
      return datasets;
    }

    @Override
    public void close() throws RuntimeException {
      if (!update) {
        batchSession.commit();
      }
      batchSession.close();
      total += _total;
      updated += _updated;
      nomatch += _nomatch;
    }
  }
}
