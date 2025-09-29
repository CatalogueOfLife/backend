package life.catalogue.matching;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import life.catalogue.api.model.IndexName;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.model.SimpleNameClassified;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxonomicStatus;

import life.catalogue.db.mapper.NameUsageMapper;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import org.gbif.nameparser.api.Rank;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
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

import org.mapdb.DataIO;

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

    var usages = ChronicleMapBuilder.of(String.class, SimpleNameCached.class)
      .name("usages")
      .averageKey(samples.get(0).getId())
      .valueMarshaller(MARSHALLER)
      .averageValue(samples.get(0))
      .entries(count)
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
  public void close() {
    usageCMap.close();
    byCanonNidx.close();
  }

  @Override
  public List<SimpleNameClassified<SimpleNameCached>> usagesByCanonicalNidx(int nidx) {
    var canonIDs = byCanonNidx.get(nidx);
    if (canonIDs != null) {
      return Arrays.stream(canonIDs)
        .map(this::getSNClassified)
        .collect(Collectors.toList());
    }
    return Collections.emptyList();
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

}
