package life.catalogue.importer.store;

import life.catalogue.importer.store.model.NameUsageData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

/**
 * A utility class to iterate over usages in the store in taxonomic order and execute any number of StartEndHandler while walking.
 */
public class TreeWalker {
  
  private static final Logger LOG = LoggerFactory.getLogger(TreeWalker.class);
  private static final int reportingSize = 10000;

  public interface StartEndHandler {
    void start(NameUsageData data);
    void end(NameUsageData data);
  }

  /**
   * Walks all usages of the taxonomic tree in a depth first order. No order of children guaranteed
   * @return the number of usages processed
   */
  public static int walkTree(ImportStore db, StartEndHandler... handler) throws InterruptedException {
    AtomicInteger counter = new AtomicInteger();
    // index by parentKey //TODO: use mapdb???
    Map<String, List<String>> children = new HashMap<>();
    db.usages().all().forEach(u -> {
      if (u.usage.getParentId() != null) {
        children.computeIfAbsent(u.usage.getParentId(), k -> new ArrayList<>())
          .add(u.getId());
      }
    });
    for (var root : db.usages().listRoot()) {
      if (Thread.currentThread().isInterrupted()) {
        throw new InterruptedException("TreeWalker thread was cancelled/interrupted");
      }
      walkUsage(new NameUsageData(db.name(root), root), db, children, handler, counter);
    }
    return counter.get();
  }

  private static void walkUsage(NameUsageData root, ImportStore db, Map<String, List<String>> children, TreeWalker.StartEndHandler[] handler, AtomicInteger counter) {
    if (counter.incrementAndGet() % reportingSize == 0) {
      LOG.debug("Processed {}", counter.get());
    }
    handleStart(root, handler);
    for (String childID : children.getOrDefault(root.ud.getId(), ImmutableList.of())) {
      var child = db.nameUsage(childID);
      walkUsage(child, db, children, handler, counter);
    }
    handleEnd(root, handler);
  }

  private static void handleStart(NameUsageData n, TreeWalker.StartEndHandler[] handler) {
    for (StartEndHandler h : handler) {
      h.start(n);
    }
  }
  
  private static void handleEnd(NameUsageData n, TreeWalker.StartEndHandler[] handler) {
    for (StartEndHandler h : handler) {
      h.end(n);
    }
  }
  
}
