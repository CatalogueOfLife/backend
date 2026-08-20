package life.catalogue.matching;

import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.model.SimpleNameClassified;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxonomicStatus;

import org.gbif.nameparser.api.Rank;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import jakarta.validation.constraints.NotNull;
import org.jetbrains.annotations.Nullable;

import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.hash.serialization.BytesReader;
import net.openhft.chronicle.hash.serialization.BytesWriter;
import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.map.ChronicleMapBuilder;

public class UsageMatcherChronicleStore extends UsageMatcherAbstractStore {
  private static final UsageMatcherChronicleStore.BytesMarshaller MARSHALLER = new UsageMatcherChronicleStore.BytesMarshaller();
  /**
   * How far beyond the configured entries() ChronicleMap may grow before it refuses to allocate another
   * segment tier. The average key/value sizes are estimated from a handful of sample usages, so they can
   * be off for a dataset with unevenly sized ids or names; without headroom such a map dies mid-load with
   * "Attempt to allocate #n extra segment tier". Extra tiers are allocated lazily, so a generous factor
   * costs no disk or memory while the estimate holds - it only degrades lookup speed once it is used.
   */
  private static final double MAX_BLOAT_FACTOR = 10;
  private final ChronicleMap<Integer, String[]> byCanonNidx;
  private final ChronicleMap<String, SimpleNameCached> usageCMap;

  /**
   * Opens existing persisted ChronicleMap files without querying the DB.
   * ChronicleMap reads sizing from the stored file header; builder params are ignored for existing files.
   */
  public static UsageMatcherChronicleStore reopen(int datasetKey, File dir) throws IOException {
    var keysF      = new File(dir, "usages");
    var canonicalF = new File(dir, "canonical");
    // Dummy sizing — overridden by values stored in the file header.
    var usages = ChronicleMapBuilder.of(String.class, SimpleNameCached.class)
      .name("usages")
      .entries(1)
      .averageKeySize(10)
      .averageValueSize(64)
      .valueMarshaller(MARSHALLER)
      .createPersistedTo(keysF);
    ChronicleMap<Integer, String[]> byCanonNidx;
    try {
      byCanonNidx = ChronicleMapBuilder.of(Integer.class, String[].class)
        .name("canonical")
        .entries(1)
        .averageValueSize(32)
        .createPersistedTo(canonicalF);
    } catch (IOException e) {
      usages.close();
      throw e;
    }
    return new UsageMatcherChronicleStore(datasetKey, usages, byCanonNidx);
  }

  /**
   * @param count      number of entries to size the usages map for (usage count incl. write headroom)
   * @param canonCount number of entries to size the canonical inverted index for. This is the number of
   *                   distinct canonical name index ids (incl. any write headroom), which is typically
   *                   well below the usage count - sizing it with {@code count} grossly over-allocates the file.
   */
  public static UsageMatcherChronicleStore build(int datasetKey, File dir, long count, long canonCount, List<SimpleNameCached> samples) throws IOException {
    if (!dir.exists()) {
      FileUtils.forceMkdir(dir);
    }
    var keysF = new File(dir, "usages");
    var canonicalF = new File(dir, "canonical");

    // make sure we have some samples and limit to 5 max
    if (samples.isEmpty()) {
      samples = List.of(UsageMatcherChronicleStore.sample("DRFTGZH"), UsageMatcherChronicleStore.sample("1234562134"));
    } else if (samples.size() > 5) {
      samples = samples.subList(0, 5);
    }

    // average across ALL samples, never just the first one: listSN orders by id, so sample 0 is the
    // textually first usage of the dataset, not a representative one. In a dataset that mixes short
    // numeric ids with long LSIDs/URLs the short ones sort first and sizing off them undersizes the map.
    double avgKeySize = samples.stream().mapToInt(sn -> utf8Length(sn.getId())).average().orElse(16);
    double avgValueSize = samples.stream().mapToInt(UsageMatcherChronicleStore::marshalledSize).average().orElse(64);

    var usages = ChronicleMapBuilder.of(String.class, SimpleNameCached.class)
      .name("usages")
      .entries(count)
      .averageKeySize(avgKeySize)
      .averageValueSize(avgValueSize)
      .maxBloatFactor(MAX_BLOAT_FACTOR)
      .valueMarshaller(MARSHALLER)
      .createPersistedTo(keysF);

    ChronicleMap<Integer, String[]> byCanonNidx;
    try {
      // realistic average fan-out: most canonical ids map to a single usage id, occasionally a few.
      // Sizing the value chunk for the real average (instead of all 5 sample ids) keeps the file small.
      int avgIds = (int) Math.max(1, Math.min(5, Math.round((double) count / Math.max(1, canonCount))));
      String[] avgValue = new String[avgIds];
      Arrays.fill(avgValue, "x".repeat((int) Math.max(1, Math.round(avgKeySize))));
      byCanonNidx = ChronicleMapBuilder.of(Integer.class, String[].class)
        .name("canonical")
        .entries(canonCount)
        .averageValue(avgValue)
        .maxBloatFactor(MAX_BLOAT_FACTOR)
        .createPersistedTo(canonicalF);
    } catch (IOException e) {
      usages.close();
      throw e;
    }

    return new UsageMatcherChronicleStore(datasetKey, usages, byCanonNidx);
  }

  private static int utf8Length(String x) {
    return x == null ? 0 : x.getBytes(StandardCharsets.UTF_8).length;
  }

  /** serialized size of one usage as {@link BytesMarshaller} writes it: a 4 byte length prefix plus the fory bytes */
  private static int marshalledSize(SimpleNameCached sn) {
    return 4 + UsageMatcherFactory.FURY.serializeJavaObject(sn).length;
  }

  static SimpleNameCached sample(String id) {
    var u = new SimpleNameCached();
    u.setId(id);
    u.setParent("p" + id);
    u.setName("Abies alba");
    u.setAuthorship("Miller, 1988");
    u.setRank(Rank.SPECIES);
    u.setStatus(TaxonomicStatus.ACCEPTED);
    u.setSectorKey(13);

    u.setCanonicalId(1345);
    u.setNamesIndexId(13451);
    u.setNamesIndexMatchType(MatchType.EXACT);
    u.setExtinct(false);
    return u;
  }

  static class BytesMarshaller implements BytesWriter<SimpleNameCached>, BytesReader<SimpleNameCached> {

    @NotNull
    @Override
    public SimpleNameCached read(Bytes<?> in, @Nullable SimpleNameCached using) {
      int size = in.readInt();
      byte[] bytes = new byte[size];
      in.read(bytes);
      return UsageMatcherFactory.FURY.deserializeJavaObject(bytes, SimpleNameCached.class);
    }

    @Override
    public void write(Bytes<?> out, @NotNull SimpleNameCached value) {
      byte[] bytes = UsageMatcherFactory.FURY.serializeJavaObject(value);
      out.writeInt(bytes.length);
      out.write(bytes);
    }
  }

  private UsageMatcherChronicleStore(int datasetKey, ChronicleMap<String, SimpleNameCached> usages, ChronicleMap<Integer, String[]> byCanonNidx) {
    super(datasetKey, usages, true);
    this.byCanonNidx = byCanonNidx;
    this.usageCMap = usages;
  }

  @Override
  public List<SimpleNameClassified<SimpleNameCached>> usagesByCanonicalId(int canonId) {
    var canonIDs = byCanonNidx.get(canonId);
    if (canonIDs != null) {
      return Arrays.stream(canonIDs)
        .map(this::getSNClassified)
        .collect(Collectors.toList());
    }
    return Collections.emptyList();
  }

  @Override
  public List<SimpleNameCached> simpleNamesByCanonicalId(int canonId) {
    var canonIDs = byCanonNidx.get(canonId);
    if (canonIDs != null) {
      return Arrays.stream(canonIDs)
        .map(this::get)
        .collect(Collectors.toList());
    }
    return Collections.emptyList();
  }

  @Override
  public Collection<Integer> allCanonicalIds() {
    return byCanonNidx.keySet();
  }

  @Override
  public int canonicalSize() {
    return byCanonNidx.size();
  }

  @Override
  public void add(SimpleNameCached sn) {
    var old = usages.put(sn.getId(), sn);
    if (sn.getCanonicalId() != null) {
      if (old != null) {
        // UPDATE
        if (!Objects.equals(old.getCanonicalId(), sn.getCanonicalId())) {
          // remove from old array
          var ids = byCanonNidx.get(old.getCanonicalId());
          int idx = ArrayUtils.indexOf(ids, old.getId());
          byCanonNidx.put(old.getCanonicalId(), ArrayUtils.remove(ids, idx));
          // add to new array
          add2Canon(sn.getCanonicalId(), sn);
        } else {
          // nothing - same canonicalID and same sn.ID
        }
      } else {
        add2Canon(sn.getCanonicalId(), sn);
      }
    }
  }

  private void add2Canon(Integer canonicalId, SimpleNameCached sn) {
    if (byCanonNidx.containsKey(canonicalId)) {
      var ids = byCanonNidx.get(canonicalId);
      byCanonNidx.put(canonicalId, ArrayUtils.add(ids, sn.getId()));
    } else {
      byCanonNidx.put(canonicalId, new String[]{sn.getId()});
    }
  }

  @Override
  protected void replaceByCanonUsageID(Integer canonicalId, String oldID, String newID) {
    var ids = byCanonNidx.get(canonicalId);
    var idx = ArrayUtils.indexOf(ids, oldID);
    ids[idx] = newID;
  }

  @Override
  public void close() {
    usageCMap.close();
    byCanonNidx.close();
  }
}
