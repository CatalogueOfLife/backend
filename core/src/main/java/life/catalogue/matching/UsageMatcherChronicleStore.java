package life.catalogue.matching;

import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.model.SimpleNameClassified;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxonomicStatus;

import org.gbif.nameparser.api.Rank;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.hash.serialization.BytesReader;
import net.openhft.chronicle.hash.serialization.BytesWriter;
import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.map.ChronicleMapBuilder;

public class UsageMatcherChronicleStore extends UsageMatcherAbstractStore {
  private static final UsageMatcherChronicleStore.BytesMarshaller MARSHALLER = new UsageMatcherChronicleStore.BytesMarshaller();
  private final ChronicleMap<Integer, String[]> byCanonNidx;
  private final ChronicleMap<String, SimpleNameCached> usageCMap;

  public static UsageMatcherChronicleStore build(int datasetKey, File dir, long count, List<SimpleNameCached> samples) throws IOException {
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

    var usages = ChronicleMapBuilder.of(String.class, SimpleNameCached.class)
      .name("usages")
      .entries(count)
      .averageKey(samples.get(0).getId())
      .averageValue(samples.get(0))
      .valueMarshaller(MARSHALLER)
      .createPersistedTo(keysF);

    var byCanonNidx = ChronicleMapBuilder.of(Integer.class, String[].class)
      .name("canonical")
      .entries(count)
      .averageValue(samples.stream().map(SimpleNameCached::getId).toArray(String[]::new))
      .createPersistedTo(canonicalF);

    return new UsageMatcherChronicleStore(datasetKey, dir, usages, byCanonNidx);
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

  private UsageMatcherChronicleStore(int datasetKey, File dir, ChronicleMap<String, SimpleNameCached> usages, ChronicleMap<Integer, String[]> byCanonNidx) {
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
