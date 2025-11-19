package life.catalogue.importer.store;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.common.kryo.map.MapDbObjectSerializer;
import life.catalogue.importer.IdGenerator;
import life.catalogue.importer.store.model.NameData;
import life.catalogue.importer.store.model.RankedUsage;
import life.catalogue.importer.store.model.RelationData;
import life.catalogue.importer.store.model.UsageData;

import life.catalogue.printer.TextTreePrinter;

import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.nameparser.api.Rank;

import java.io.File;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.apache.commons.io.FileUtils;
import org.mapdb.DB;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.Pool;
import com.google.common.base.Preconditions;

import static life.catalogue.common.tax.NameFormatter.HYBRID_MARKER;

/**
 * A persistence mechanism for storing core taxonomy & names propLabel and relations in an embedded
 * Neo4j database, while keeping a large BLOB of information in a separate MapDB storage.
 * <p>
 * Neo4j does not perform well storing large propLabel in its node and it is recommended to keep
 * large BLOBs or strings externally: https://neo4j.com/blog/dark-side-neo4j-worst-practices/
 * <p>
 * We use the Kryo library for a very performant binary
 * serialisation with the data keyed under the neo4j node value.
 */
public class ImportStore implements AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(ImportStore.class);

  private final int datasetKey;
  private final int attempt;
  private final DB mapDb;
  private final File storeDir;

  // verbatimKey sequence and lookup
  private final AtomicInteger verbatimSequence = new AtomicInteger(0);
  private final Map<Integer, VerbatimRecord> verbatim;
  private final ReferenceMapStore references;
  private final MapStore<TypeMaterial> typeMaterial;
  private final NameStore names;
  private final UsageStore usages;

  private final IdGenerator idGen = new IdGenerator("~");


  ImportStore(int datasetKey, int attempt, DB mapDb, File storeDir, Pool<Kryo> pool) {
    this.datasetKey = datasetKey;
    this.attempt = attempt;
    this.storeDir = storeDir;
    this.mapDb = mapDb;
    
    try {
      verbatim = mapDb.hashMap("verbatim")
          .keySerializer(Serializer.INTEGER)
          .valueSerializer(new MapDbObjectSerializer(VerbatimRecord.class, pool, 128))
          .createOrOpen();
      references = new ReferenceMapStore(mapDb, pool, this::addIssues);
      typeMaterial = new MapStore<>(TypeMaterial.class, "tm", mapDb, pool, this::addIssues);
      usages = new UsageStore(mapDb, "usages", pool, idGen, this);
      names = new NameStore(mapDb, "names", pool, idGen, this);

    } catch (Exception e) {
      LOG.error("Failed to initialize a new NeoDB", e);
      close();
      throw new RuntimeException(e);
    }
  }
  
  /**
   * Fully closes the db and removes all its persistence files, leaving any potentially existing persistence files untouched.
   */
  public void close() {
    LOG.info("Closing and deleting import storage for dataset {}", datasetKey);
    try {
      if (mapDb != null && !mapDb.isClosed()) {
        mapDb.close();
      }
    } catch (Exception e) {
      LOG.info("Failed to close mapDb for directory {}", storeDir.getAbsolutePath(), e);
    }

    if (storeDir != null && storeDir.exists()) {
      LOG.debug("Deleting storeDir {}", storeDir.getAbsolutePath());
      FileUtils.deleteQuietly(storeDir);
    }
  }

  public int getDatasetKey() {
    return datasetKey;
  }

  public NameStore names() {
    return names;
  }

  public void addNameRelation(RelationData<NomRelType> r) {
    var n = names().objByID(r.getFromID());
    if (n != null) {
      n.addRelation(r);
      names().update(n);
    } else {
      LOG.warn("No name found for relation {} {}", r.getFromID(), r.getType());
    }
  }

  public UsageStore usages() {
    return usages;
  }

  public ReferenceMapStore references() {
    return references;
  }

  public MapStore<TypeMaterial> typeMaterial() {
    return typeMaterial;
  }

  public UsageData usageWithName(String usageID) {
    var u = usages().objByID(usageID);
    loadName(u);
    return u;
  }

  public NameData loadName(UsageData u) {
    if (u != null && u.nameID != null && u.usage.getName() == null) {
      NameData nn = names().objByID(u.nameID);
      u.usage.setName(nn.getName());
      return nn;
    }
    return null;
  }

  /**
   * Returns a stream over all bare names.
   */
  public Stream<NameData> bareNames() {
    return names().all().filter(n -> n.usageIDs.isEmpty());
  }

  public Set<String> usageIDsByNames(Rank rank, boolean inclUnranked, String... scientificName) {
    Set<String> nodes = new HashSet<>();
    if (scientificName != null && scientificName.length > 0) {
      Set<String> names = Arrays.stream(scientificName)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
      for (String name : names) {
        nodes.addAll(usageIDsByName(name, null, rank, inclUnranked));
      }
    }
    return nodes;
  }

  /**
   * Retuns a list of usage ids that have a matching scientific name, rank & authorship.
   * A prefixed hybrid symbol will be ignored in both the query name and stored names.
   */
  public Set<String> usageIDsByName(String scientificName, @Nullable String authorship, @Nullable Rank rank, boolean inclUnranked) {
    Set<String> nameIDs = names().nameIdsByName(scientificName);
    if (scientificName.charAt(0) != HYBRID_MARKER) {
      // try also to find the hybrid version of any monomial
      nameIDs.addAll( names().nameIdsByName(HYBRID_MARKER + " " + scientificName));
    }
    // load names
    Set<NameData> names = nameIDs.stream()
      .map(names()::objByID)
      .collect(Collectors.toSet());
    // filter ranks
    if (rank != null) {
      names.removeIf(n -> {
        Rank r = n.getName().getRank();
        if (inclUnranked) {
          return !r.equals(rank) && r != Rank.UNRANKED;
        } else {
          return !r.equals(rank);
        }
      });
    }
    // filter authorship
    if (authorship != null) {
      names.removeIf(n -> !authorship.equalsIgnoreCase(n.getName().getAuthorship()));
    }

    Set<String> taxa = new HashSet<>();
    for (var n : names) {
      taxa.addAll(n.usageIDs);
    }
    return taxa;
  }

  public boolean createNameAndUsage(UsageData u) {
    Preconditions.checkNotNull(u.usage.getName(), "NeoUsage with name required");

    // is no true verbatim record existed create a new one to hold issues for validation etc.
    if (u.usage.getVerbatimKey() == null) {
      VerbatimRecord v = new VerbatimRecord();
      put(v);
      u.usage.setVerbatimKey(v.getId());
    }
    // first create the name, potentially assigning an id
    NameData nn = new NameData(u.usage.getName());
    if (nn.getId() == null) {
      nn.setId(u.getId());
    }
    if (nn.getVerbatimKey() == null) {
      nn.setVerbatimKey(u.getVerbatimKey());
    }
    if (nn.getName().getOrigin() == null) {
      nn.getName().setOrigin(u.asNameUsageBase().getOrigin());
    }
    nn.homotypic = u.homotypic;
    var created = names.create(nn);
    if (created) {
      u.nameID = nn.getId();
      if (!u.usage.isBareName()) {
        created = usages.create(u);
        if (!created) {
          u.setId(null); // non unique id
        }
      }
    } else {
      LOG.debug("Skip usage {} as no name was persisted for {}", u.getId(), nn.getName().getLabel());
    }
    return created;
  }

  /**
   * Creates or updates a verbatim record.
   * If created a new key is issued.
   */
  public void put(VerbatimRecord v) {
    if (v.hasChanged()) {
      if (v.getId() == null) {
        v.setId(verbatimSequence.incrementAndGet());
      }
      verbatim.put(v.getId(), v);
      v.setHashCode();
    }
  }

  /**
   * @return the verbatim record belonging to the requested key as assigned from verbatimSequence
   */
  public VerbatimRecord getVerbatim(int key) {
    VerbatimRecord rec = verbatim.get(key);
    if (rec != null) {
      rec.setHashCode();
    }
    return rec;
  }
  
  /**
   * @return a lazy supplier for the verbatim record belonging to the requested key as assigned from verbatimSequence
   */
  public Supplier<VerbatimRecord> verbatimSupplier(int key) {
    return new Supplier<VerbatimRecord>() {
      @Override
      public VerbatimRecord get() {
        return getVerbatim(key);
      }
    };
  }

  public NameData addNameIssues(String nameID, Issue... issue) {
    NameData nn = names.objByID(nameID);
    addIssues(nn, issue);
    return nn;
  }

  public UsageData addUsageIssues(String usageID, Issue... issue) {
    UsageData nu = usages.objByID(usageID);
    addIssues(nu, issue);
    return nu;
  }

  public void addIssues(VerbatimEntity ent, Issue... issue) {
    addIssues(ent.getVerbatimKey(), issue);
  }
  
  public void addIssues(Integer verbatimKey, Issue... issue) {
    if (verbatimKey != null) {
      VerbatimRecord v = getVerbatim(verbatimKey);
      if (v == null) {
        LOG.warn("No verbatim exists for verbatim key {}", verbatimKey);
      } else {
        for (Issue is : issue) {
          if (is != null) {
            v.add(is);
          }
        }
        put(v);
      }
    }
  }

  public int verbatimSize() {
    return verbatim.size();
  }

  public Iterable<VerbatimRecord> verbatimList() {
    return verbatim.values();
  }

  public Stream<VerbatimRecord> verbatimList(Term rowType) {
    return verbatim.values().stream().filter(v -> v.getType().equals(rowType));
  }

  public void debug() {
    System.out.println("\n\nUsage Map IDs:");
    usages().logKeys();

    System.out.println("\n\nName Map IDs:");
    names().logKeys();
  }

  /**
   * Creates a new taxon in neo and the name usage kvp using the source usages as a template for the classification propLabel.
   * Only copies the classification above genus and ignores genus and below!
   * A verbatim usage is created with just the parentNameUsage(ID) values so they can getUsage resolved into proper neo relations later.
   * Name and taxon ids are generated de novo.
   *
   * @param name                the new name to be used
   * @param source              the taxon source to copyTaxon from
   * @param excludeRankAndBelow the rank (and all ranks below) to exclude from the source classification
   */
  public RankedUsage createProvisionalUsageFromSource(Origin origin,
                                                      Name name,
                                                      @Nullable UsageData source,
                                                      Rank excludeRankAndBelow) {
    UsageData u = UsageData.createTaxon(origin, name, TaxonomicStatus.PROVISIONALLY_ACCEPTED);
    // copyTaxon verbatim classification from source
    if (source != null) {
      if (source.classification != null) {
        u.classification = new Classification(source.classification);
        // remove lower ranks
        u.classification.clearRankAndBelow(excludeRankAndBelow);
      }
      // copyTaxon parent props from source
      if (u.getVerbatimKey() != null) {
        VerbatimRecord sourceTerms = getVerbatim(u.getVerbatimKey());
        VerbatimRecord copyTerms = new VerbatimRecord();
        copyTerms.put(DwcTerm.parentNameUsageID, sourceTerms.get(DwcTerm.parentNameUsageID));
        copyTerms.put(DwcTerm.parentNameUsage, sourceTerms.get(DwcTerm.parentNameUsage));
        put(copyTerms);
        u.setVerbatimKey(copyTerms.getId());
      }
    }
    
    // store, which creates a new neo node
    createNameAndUsage(u);

    return new RankedUsage(u.getId(), u.isSynonym(), u.nameID, name.getScientificName(), name.getAuthorship(), name.getRank());
  }
  
  public void updateIdGenerators() {
    // just update prefix so new ids for implicit usages are good
    idGen.setPrefix("x",
        Stream.concat(
            usages().allIds(),
            names().allIds()
        )
    );
    LOG.info("Name/Usage ID generator updated with unique prefix {}", idGen.getPrefix());
  }
  
  public void reportDuplicates() {
    if (names().getDuplicateCounter() > 0) {
      LOG.warn("The inserted dataset contains {} duplicate nameIds! Only the first record will be used", names().getDuplicateCounter());
    }
    if (usages().getDuplicateCounter() > 0) {
      LOG.warn("The inserted dataset contains {} duplicate taxonIds! Only the first record will be used", usages().getDuplicateCounter());
    }
  }

  public String printTree() throws InterruptedException {
    StringWriter w = new StringWriter();
    TextTreePrinter printer = new TextTreePrinter(null,null,null,null,null,null,w);
    TreeWalker.walkTree(this, new TreeWalker.StartEndHandler() {
      @Override
      public void start(UsageData data) {
        printer.accept(data.toSimpleName());
      }

      @Override
      public void end(UsageData data) {
      }
    });
    return w.toString();
  }
}

