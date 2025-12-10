package life.catalogue.importer.store;

import life.catalogue.api.model.NameUsageBase;
import life.catalogue.api.vocab.NomRelType;
import life.catalogue.importer.store.model.NameUsageData;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import life.catalogue.importer.txttree.TxtTreeInserter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nullable;

/**
 * A utility class to iterate over usages in the store in taxonomic order and execute any number of StartEndHandler while walking.
 */
public class TreeWalker {
  private static final Logger LOG = LoggerFactory.getLogger(TreeWalker.class);
  private static final int reportingSize = 10000;
  private static final Comparator<NameUsageData> BY_RANK_N_NAME = Comparator
    .comparing((NameUsageData nu) -> nu.nd.getRank())
    .thenComparing(TreeWalker::sortableName);

  public interface StartEndHandler {
    void start(NameUsageData data, WalkerContext ctxt);
    void end(NameUsageData data, WalkerContext ctxt);
  }

  private static String sortableName(NameUsageData nu) {
    return nu.getLabel(true);
  }

  public static int walkTree(ImportStore db, StartEndHandler... handler) throws InterruptedException {
    return walkTree(db, null, handler);
  }

  /**
   * Walks all usages of the taxonomic tree in a depth first order.
   * Children are taxonomically ordered by their rank and scientific name.
   * @return the number of usages processed
   */
  public static int walkTree(ImportStore db, @Nullable Consumer<WalkerContext> contextConsumer, StartEndHandler... handler) throws InterruptedException {
    var ctxt = buildContext(db);
    if (contextConsumer != null) {
      contextConsumer.accept(ctxt);
    }
    var roots = db.usages().listRoot().stream()
      .map(db::nameUsage)
      .sorted(BY_RANK_N_NAME)
      .toList();
    for (var root : roots) {
      if (Thread.currentThread().isInterrupted()) {
        throw new InterruptedException("TreeWalker thread was cancelled/interrupted");
      }
      walkUsage(root, ctxt, handler);
    }
    return ctxt.counter.get();
  }

  public static class WalkerContext {
    final ImportStore db ;
    final AtomicInteger counter = new AtomicInteger();
    // index by parentKey TODO: use mapdb???
    final Map<String, List<String>> children = new HashMap<>();
    // index basionym usage ids TODO: use mapdb???
    final Set<String> basionyms = new HashSet<>();

    public WalkerContext(ImportStore db) {
      this.db = db;
    }
  }

  private static WalkerContext buildContext(ImportStore db) {
    final var ctxt = new WalkerContext(db);
    db.usages().all().forEach(u -> {
      if (u.usage.getParentId() != null) {
        ctxt.children.computeIfAbsent(u.usage.getParentId(), k -> new ArrayList<>())
          .add(u.getId());
      }
    });

    db.names().all().forEach(n -> {
      var brel = n.getRelation(NomRelType.BASIONYM);
      if (brel != null) {
        var bn = db.names().objByID(brel.getToID());
        ctxt.basionyms.addAll(bn.usageIDs);
      }
    });
    return ctxt;
  }

  private static void walkUsage(NameUsageData node, WalkerContext ctxt, TreeWalker.StartEndHandler[] handler) {
    if (ctxt.counter.incrementAndGet() % reportingSize == 0) {
      LOG.debug("Processed {}", ctxt.counter.get());
    }
    handleStart(node, ctxt, handler);
    var childUsages = ctxt.children.getOrDefault(node.ud.getId(), ImmutableList.of()).stream()
      .map(ctxt.db::nameUsage)
      .toList();
    final Comparator<NameUsageData> synComp = Comparator
      .comparing((NameUsageData nu) -> !ctxt.basionyms.contains(nu.ud.getId()))
      .thenComparing(TreeWalker::sortableName);
    var synonyms = childUsages.stream()
      .filter(u->u.ud.isSynonym())
      .sorted(synComp)
      .toList();
    var children = childUsages.stream()
      .filter(u->u.ud.isTaxon())
      .sorted(BY_RANK_N_NAME)
      .toList();
    for (var child : synonyms) {
      walkUsage(child, ctxt, handler);
    }
    for (var child : children) {
      walkUsage(child, ctxt, handler);
    }
    handleEnd(node, ctxt, handler);
  }

  private static void handleStart(NameUsageData n, WalkerContext ctxt, StartEndHandler[] handler) {
    for (StartEndHandler h : handler) {
      h.start(n, ctxt);
    }
  }
  
  private static void handleEnd(NameUsageData n, WalkerContext ctxt, StartEndHandler[] handler) {
    for (StartEndHandler h : handler) {
      h.end(n, ctxt);
    }
  }
  
}
