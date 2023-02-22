package life.catalogue.dao;

import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.SimpleNameClassified;
import life.catalogue.concurrent.UsageCounter;
import life.catalogue.db.mapper.NameUsageMapper;

import org.gbif.nameparser.api.Rank;

import java.io.Closeable;
import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The returned streams encapsulates a database cursor.
 * If timely disposal of resources is required, the try-with-resources construct should be used to ensure that the stream's close method is invoked after the stream operations are completed.
 */
public class TreeStreams {
  private static final Logger LOG = LoggerFactory.getLogger(TreeStreams.class);

  /**
   * Make sure to close the iterator at the end to release the underlying database cursor !!!
   */
  public static Stream<SimpleNameClassified<SimpleName>> dataset(SqlSession session, int datasetKey, boolean includeSynonyms, Boolean extinct, @Nullable String startID, @Nullable Rank lowestRank) {
    LOG.debug("Streaming simple tree for dataset {}", datasetKey);
    var num = session.getMapper(NameUsageMapper.class);
    var c = num.processTreeSimple(datasetKey, null, startID, null, lowestRank, extinct, includeSynonyms);
    return streamCursor(datasetKey, c);
  }

  private static Stream<SimpleNameClassified<SimpleName>> streamCursor(int datasetKey, final Cursor<SimpleName> c) {
    final var iter = new TreeIterator(datasetKey, c);
    var stream = StreamSupport.stream(Spliterators.spliteratorUnknownSize(iter, Spliterator.ORDERED), false);
    return stream.onClose(iter::close);
  }

  private static class TreeIterator implements Iterator<SimpleNameClassified<SimpleName>>, Closeable {
    private final int datasetKey;
    private final Cursor<SimpleName> cursor;
    private final Iterator<SimpleName> iter;
    public final UsageCounter counter = new UsageCounter();
    protected final LinkedList<SimpleName> parents = new LinkedList<>();

    TreeIterator(int datasetKey, Cursor<SimpleName> cursor) {
      this.datasetKey = datasetKey;
      this.cursor = cursor;
      iter = cursor.iterator();
    }

    @Override
    public boolean hasNext() {
      return iter.hasNext();
    }

    @Override
    public SimpleNameClassified<SimpleName> next() {
      var sn = iter.next();

      // parent list still applies?
      while (!parents.isEmpty() && !parents.peekLast().getId().equals(sn.getParent())) {
        parents.removeLast();
      }

      counter.inc(sn);
      var snc = new SimpleNameClassified<>(sn);
      snc.setClassification(List.copyOf(parents));

      // track parents
      parents.add(sn);

      return snc;
    }

    @Override
    public void close() {
      try {
        LOG.info("Closing db cursor for dataset {}", datasetKey);
        cursor.close();
      } catch (IOException e) {
        throw new RuntimeException(e);
      } finally {
        LOG.info("Streamed simple tree for dataset {}: {}", datasetKey, counter);
      }

    }
  }
}
