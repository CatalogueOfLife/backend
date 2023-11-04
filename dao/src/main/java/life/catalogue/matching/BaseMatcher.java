package life.catalogue.matching;

import life.catalogue.api.model.IndexName;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.NameMatch;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.db.mapper.ArchivedNameUsageMatchMapper;
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

  class BulkMatchHandlerNames extends BulkMatchHandler {
    NameMatchMapper nmm;

    BulkMatchHandlerNames(int datasetKey, boolean allowInserts) {
      super(datasetKey, allowInserts);
      nmm = batchSession.getMapper(NameMatchMapper.class);
    }

    @Override
    void persist(Name n, NameMatch m, MatchType oldType, Integer oldId) {
      if (oldType == null) {
        nmm.create(n, n.getSectorKey(), n.getNamesIndexId(), m.getType());
      } else {
        nmm.update(n, n.getNamesIndexId(), m.getType());
      }
    }
  }

  class BulkMatchHandlerArchivedUsages extends BulkMatchHandler {
    ArchivedNameUsageMatchMapper nmm;

    BulkMatchHandlerArchivedUsages(int datasetKey, boolean allowInserts) {
      super(datasetKey, allowInserts);
      nmm = batchSession.getMapper(ArchivedNameUsageMatchMapper.class);
    }

    @Override
    void persist(Name n, NameMatch m, MatchType oldType, Integer oldId) {
      if (oldType == null) {
        nmm.create(n, n.getNamesIndexId(), m.getType());
      } else {
        nmm.update(n, n.getNamesIndexId(), m.getType());
      }
    }
  }

  abstract class BulkMatchHandler implements Consumer<Name>, AutoCloseable {
    private final int datasetKey;
    private final boolean allowInserts;
    final SqlSession batchSession;
    private int _total = 0;
    private int _updated = 0;
    private int _nomatch = 0;

    BulkMatchHandler(int datasetKey, boolean allowInserts) {
      this.datasetKey = datasetKey;
      this.allowInserts = allowInserts;
      this.batchSession = factory.openSession(ExecutorType.BATCH, false);
    }

    @Override
    public void accept(Name n) {
      _total++;
      final Integer oldId = n.getNamesIndexId();
      final MatchType oldType = n.getNamesIndexType();
      NameMatch m = ni.match(n, allowInserts, false);
      if (!m.hasMatch()) {
        _nomatch++;
        LOG.debug("No match for {} from dataset {} with {} alternatives: {}", n.toStringComplete(), datasetKey,
          m.getAlternatives() == null ? 0 : m.getAlternatives().size(),
          m.getAlternatives() == null ? "" : m.getAlternatives().stream().map(IndexName::getLabelWithRank).collect(Collectors.joining("; "))
        );
      }
      Integer newKey = m.hasMatch() ? m.getName().getKey() : null;
      if (oldType == null || !Objects.equals(oldId, newKey)) {
        persist(n, m, oldType, oldId);
        if (_updated++ % 10000 == 0) {
          batchSession.commit();
          LOG.debug("Updated {} name matches for {} names with {} no matches for dataset {}", _updated, _total, _nomatch, datasetKey);
        }
      }
    }

    abstract void persist(Name n, NameMatch m, MatchType oldType, Integer oldId);

    @Override
    public void close() throws RuntimeException {
      batchSession.commit();
      batchSession.close();
      total += _total;
      updated += _updated;
      nomatch += _nomatch;
    }
  }
}
