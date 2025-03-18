package life.catalogue.matching;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.matching.nidx.NameIndex;
import life.catalogue.matching.nidx.NameIndexFactory;
import life.catalogue.matching.nidx.NamesIndexConfig;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.Pool;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.hash.serialization.BytesReader;
import net.openhft.chronicle.hash.serialization.BytesWriter;
import net.openhft.chronicle.hash.serialization.ListMarshaller;
import net.openhft.chronicle.map.ChronicleMap;
import net.openhft.chronicle.map.ChronicleMapBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * No cache involved but always queries postgres directly
 */
public class MatchingStorageChrononicle implements MatchingStorage<SimpleNameCached>, Closeable {
  private static final Logger LOG = LoggerFactory.getLogger(MatchingStorageChrononicle.class);
  private final static int MAP_SPACE = 100;
  private final SimpleNameCachedKryoPool pool;
  private final Pool<Output> outputs;
  private final Pool<Input> inputs;

  private final MatchingStorageMetadata metadata;
  private final ChronicleMap<String, SimpleNameCached> usage;
  private final Map<String, ParsedUsage> pUsage;
  private final ChronicleMap<Integer, List<SimpleNameCached>> byCanonNidx;
  private final NameIndex nidx;


  public static MatchingStorageChrononicle open(File dir, int poolSize) throws IOException {
    File mf = metadataFile(dir);
    ObjectMapper om = new ObjectMapper();
    var metadata = om.readValue(mf, MatchingStorageMetadata.class);
    return new MatchingStorageChrononicle(dir, poolSize, metadata);
  }

  public static MatchingStorageChrononicle create(File dir, int poolSize, MatchingStorageMetadata metadata) throws IOException {
    if (dir.exists()) {
      LOG.warn("{} already exists. Wipe it", dir);
      FileUtils.deleteQuietly(dir);
    }
    FileUtils.forceMkdir(dir);
    File mf = metadataFile(dir);
    ObjectMapper om = new ObjectMapper();
    om.writeValue(mf, metadata);
    return new MatchingStorageChrononicle(dir, poolSize, metadata);
  }

  public MatchingStorageChrononicle(File dir, int poolSize, MatchingStorageMetadata metadata) throws IOException {
    this.metadata = metadata;
    this.pool = new SimpleNameCachedKryoPool(poolSize);;
    outputs = new Pool<>(true, false, poolSize) {
      protected Output create () {
        return new Output(1024, -1);
      }
    };
    inputs = new Pool<>(true, false, poolSize) {
      protected Input create () {
        return new Input(1024);
      }
    };

    var marshaller = new SimpleNameCachedBytesMarshaller();

    var b1 = ChronicleMapBuilder.of(String.class, SimpleNameCached.class)
      .name("usage")
      .entries(metadata.getNumUsages() + MAP_SPACE)
      .averageKey("s34de5fr6")
      .valueMarshaller(marshaller)
      .averageValueSize(60)
      ;
    usage = b1.createPersistedTo(new File(dir, "usage"));

    pUsage = new HashMap<>();

    var b2 = ChronicleMapBuilder.of(Integer.class, (Class<List<SimpleNameCached>>) (Class) List.class)
      .name("canon")
      .entries(metadata.getNumCanonicals() + MAP_SPACE)
      .valueMarshaller(ListMarshaller.of(marshaller))
      .averageValueSize(120);
    byCanonNidx = b2.createPersistedTo(new File(dir, "canon"));

    NamesIndexConfig nCfg = new NamesIndexConfig();
    nCfg.type = NamesIndexConfig.Store.CHRONICLE;
    nCfg.maxEntries = metadata.getNumNidx() + MAP_SPACE;
    nCfg.file = new File(dir, "nidx");
    nCfg.kryoPoolSize = poolSize;
    nCfg.verification = false;
    nidx = NameIndexFactory.build(nCfg, AuthorshipNormalizer.INSTANCE).started();
  }


  private static File metadataFile(File dir) {
    return new File(dir, "metadata.json");
  }

  public NameIndex getNameIndex() {
    return nidx;
  }

  public MatchingService<SimpleNameCached> newMatchingService() {
    return new MatchingService<>(nidx, this);
  }

  @Override
  public void close() throws IOException {
    byCanonNidx.close();
    usage.close();
    try {
      nidx.stop();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private final static class SimpleNameCachedKryoPool extends Pool<Kryo> {

    public SimpleNameCachedKryoPool(int size) {
      super(true, true, size);
    }

    @Override
    public Kryo create() {
      Kryo kryo = new Kryo();
      kryo.setRegistrationRequired(true);
      kryo.register(SimpleNameCached.class);
      kryo.register(SimpleNameWithNidx.class);
      kryo.register(SimpleName.class);
      kryo.register(MatchType.class);
      kryo.register(Rank.class);
      kryo.register(NomCode.class);
      kryo.register(TaxonomicStatus.class);
      kryo.register(ArrayList.class);
      return kryo;
    }
  }

  private final class SimpleNameCachedBytesMarshaller implements BytesWriter<SimpleNameCached>, BytesReader<SimpleNameCached> {

    @NotNull
    @Override
    public SimpleNameCached read(Bytes in, @Nullable SimpleNameCached using) {
      Kryo kryo = null;
      Input input = null;
      try {
        kryo = pool.obtain();
        int size = in.readInt();
        byte[] bytes = new byte[size];
        in.read(bytes);

        input = inputs.obtain();
        input.setBuffer(bytes);
        return kryo.readObject(input, SimpleNameCached.class);
      } finally {
        if (kryo != null) {
          pool.free(kryo);
        }
        if (input != null) {
          inputs.free(input);
        }
      }
    }

    @Override
    public void write(Bytes out, @NotNull SimpleNameCached value) {
      Kryo kryo = null;
      Output output = null;
      try {
        kryo = pool.obtain();
        output = outputs.obtain();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(128);
        output.setOutputStream(buffer);
        kryo.writeObject(output, value);
        output.close();
        out.write(buffer.toByteArray());
      } finally {
        if (kryo != null) {
          pool.free(kryo);
        }
        if (output != null) {
          outputs.free(output);
        }
      }
    }
  }

  @Override
  public int datasetKey() {
    return metadata.getDatasetKey();
  }

  public MatchingStorageMetadata metadata() {
    return metadata;
  }

  @Override
  public SimpleNameCached convert(NameUsageBase nu, Integer canonNidx) {
    return new SimpleNameCached(nu, canonNidx);
  }

  @Override
  public void clear(int canonNidx) {
    // we don't cache anything
  }

  @Override
  public List<SimpleNameCached> get(int canonNidx) {
    return byCanonNidx.get(canonNidx);
  }

  public ParsedUsage getParsedUsage(String key) {
    return pUsage.get(key);
  }

  @Override
  public void put(int canonNidx, List<SimpleNameCached> before) {
    byCanonNidx.put(canonNidx, before);
  }
  public void put(SimpleNameCached snc) {
    usage.put(snc.getId(), snc);
  }

  @Override
  public List<SimpleNameCached> getClassification(String key) {
    List<SimpleNameCached> classification = new ArrayList<>();
    String pid = key;
    while (pid != null) {
      var p = usage.get(pid);
      classification.add(p);
      pid = p.getParentId();
    }
    return classification;
  }

  @Override
  public void clear(String usageKey) {
    // we don't cache anything
  }

}
