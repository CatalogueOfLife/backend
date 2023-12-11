package life.catalogue.matching;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import life.catalogue.api.model.IndexName;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.NameMatch;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.db.mapper.ArchivedNameUsageMatchMapper;
import life.catalogue.db.mapper.MatchMapper;
import life.catalogue.db.mapper.NameMatchMapper;

import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
      final MatchType oldType = n.getNamesIndexType();
      NameMatch m = ni.match(n, allowInserts, false);
      if (!m.hasMatch()) {
        _nomatch++;
        // we only log here, but persist below
        LOG.debug("No match for {} from dataset {} with {} alternatives: {}", n.toStringComplete(), n.getDatasetKey(),
          m.getAlternatives() == null ? 0 : m.getAlternatives().size(),
          m.getAlternatives() == null ? "" : m.getAlternatives().stream().map(IndexName::getLabelWithRank).collect(Collectors.joining("; "))
        );
      }
      if (!Objects.equals(oldType, m.getType()) || !Objects.equals(oldId, m.getNameKey())) {
        persist(n, m, oldType);
        if (_updated++ % 10000 == 0) {
          if (!update) {
            batchSession.commit();
          }
          LOG.debug("Updated {} name matches for {} names with {} no matches", _updated, _total, _nomatch);
        }
      }
    }

    void persist(Name n, NameMatch m, MatchType oldType) {
      datasets.add(n.getDatasetKey());
      if (update && oldType != null) {
        // the update might not have found a record (e.g. because we did not store NONE matches before)
        // create a record if it wasnt updated
        if (nmm.update(n, m.getNameKey(), m.getType()) < 1) {
          nmm.create(n, n.getSectorKey(), m.getNameKey(), m.getType());
        }
      } else {
        nmm.create(n, n.getSectorKey(), m.getNameKey(), m.getType());
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
