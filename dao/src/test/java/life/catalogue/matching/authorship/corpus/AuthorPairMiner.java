package life.catalogue.matching.authorship.corpus;

import life.catalogue.common.io.TabWriter;
import life.catalogue.matching.authorship.corpus.LabelRules.Label;
import life.catalogue.matching.authorship.corpus.LabelRules.Labelled;
import life.catalogue.matching.authorship.corpus.LabelRules.Source;

import org.gbif.nameparser.api.NomCode;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

/**
 * Mines labelled pairs of author citations from an export of names that share a names index id.
 * <p>
 * Pass one counts on how many names two {@link AuthorKey}s meet across datasets and inside one dataset,
 * {@link LabelRules} turns the counts into labels, and pass two reads the export again to write every labelled
 * pair with one pair of names that shows it. Nothing but counters is held in memory.
 * <p>
 * This class must not use AuthorshipNormalizer or AuthorComparator: its labels are what they get measured with.
 *
 * Run it in a forked JVM, paths are relative to the dao module. See docs/AUTHOR-CORPUS.md
 * <pre>
 * mvn -q -pl dao test-compile exec:exec -Dexec.executable=java -Dexec.classpathScope=test \
 *   -Dexec.args="-Xmx4g -cp %classpath life.catalogue.matching.authorship.corpus.AuthorPairMiner \
 *     /path/to/author-corpus.tsv.gz target/author-corpus/pairs.tsv.gz"
 * </pre>
 */
public class AuthorPairMiner {
  static final int MAX_KEYS_PER_GROUP = 20;
  private static final Pattern YEAR = Pattern.compile("(?<![0-9])([0-9]{4})(?![0-9])");
  private static final int ID_BITS = 28;
  private static final String[] SUPPORT_BUCKETS = {"0", "1", "2", "3-4", "5-9", "10-99", "100+"};

  /** how the years of two names relate */
  enum YearRel {AGREE, NONE, CONFLICT}

  public static class Stats {
    public long rows;
    public int groups;
    /** groups with names from several datasets */
    public int multiDataset;
    /** groups skipped for holding too many different keys */
    public int capped;
    /** dataset pairs of a group citing the name alike */
    public long agreeing;
    /** dataset pairs of a group left with more than one key on a side */
    public long ambiguous;
    /** pairs of names from two nomenclatural codes */
    public long crossCode;
    public int keys;
    /** key pairs seen across datasets */
    public int crossPairs;
    /** key pairs seen inside a dataset */
    public int intraPairs;
    public final Map<Label, Integer> labelled = new EnumMap<>(Label.class);
    public final Map<Source, Integer> sources = new EnumMap<>(Source.class);
    /** key pairs seen across datasets by support and label */
    public final Map<String, Map<Label, Integer>> supportHistogram = new LinkedHashMap<>();

    Stats() {
      for (Label l : Label.values()) {
        labelled.put(l, 0);
      }
      for (String b : SUPPORT_BUCKETS) {
        supportHistogram.put(b, new EnumMap<>(Label.class));
      }
    }

    public String render(LabelRules rules, int maxKeys) {
      StringBuilder sb = new StringBuilder();
      sb.append("rules: ").append(rules).append(", maxKeysPerGroup=").append(maxKeys).append('\n');
      sb.append(String.format("rows %,d, groups %,d (several datasets %,d, skipped for too many keys %,d), keys %,d%n",
        rows, groups, multiDataset, capped, keys));
      sb.append(String.format("dataset pairs: agreeing %,d, ambiguous %,d, names of two codes %,d%n", agreeing, ambiguous, crossCode));
      sb.append(String.format("key pairs: across datasets %,d, inside a dataset %,d%n", crossPairs, intraPairs));
      sb.append("labels: ").append(labelled).append('\n');
      sb.append("sources of the labelled: ").append(sources).append('\n');
      sb.append("key pairs across datasets by support:\n");
      supportHistogram.forEach((bucket, counts) -> sb.append(String.format("  %-6s %s%n", bucket, counts)));
      return sb.toString();
    }
  }

  private interface Sink {
    /**
     * @param a the name holding the key with the smaller id
     */
    void accept(boolean intra, long pair, YearRel rel, @Nullable NomCode code, ExportRow a, ExportRow b);
  }

  private record Obs(YearRel rel, @Nullable NomCode code, ExportRow a, ExportRow b) {
  }

  private final LabelRules rules;
  private final int maxKeys;
  private final Stats stats = new Stats();

  private final Object2IntOpenHashMap<String> keyIds = new Object2IntOpenHashMap<>();
  private final List<String> keys = new ArrayList<>();
  private final IntArrayList freq = new IntArrayList();
  private final Long2IntOpenHashMap pairIdx = new Long2IntOpenHashMap();
  private final IntArrayList support = new IntArrayList();
  private final IntArrayList yearAgree = new IntArrayList();
  private final IntArrayList yearConflict = new IntArrayList();
  private final IntArrayList intraYearDiff = new IntArrayList();
  private final IntArrayList intraNoYear = new IntArrayList();
  private final IntArrayList intraYearAgree = new IntArrayList();

  /**
   * @param maxKeys groups with more different keys are skipped. A name cited in dozens of ways cannot be
   *                labelled, and its pairs grow with the square
   */
  public AuthorPairMiner(LabelRules rules, int maxKeys) {
    this.rules = rules;
    this.maxKeys = maxKeys;
    keyIds.defaultReturnValue(-1);
    pairIdx.defaultReturnValue(-1);
  }

  public Stats mine(File export, File pairsOut) throws IOException {
    // pass 1: count
    CorpusIO.readGroups(export, group -> observe(group, true, this::count));
    stats.keys = keys.size();

    Labelled[] labels = label();

    // pass 2: write every labelled pair with the first names that show it
    BitSet written = new BitSet(labels.length);
    try (TabWriter writer = CorpusIO.pairWriter(pairsOut)) {
      CorpusIO.readGroups(export, group -> observe(group, false, (intra, pair, rel, code, a, b) -> {
        int idx = pairIdx.get(pair);
        Labelled l = labels[idx];
        if (!written.get(idx) && shows(l, idx, intra, rel)) {
          written.set(idx);
          write(writer, l, idx, code, a, b);
        }
      }));
    }
    String parser = CorpusIO.readParser(export);
    if (parser != null) {
      CorpusIO.writeParser(pairsOut, parser);
    }
    return stats;
  }

  /**
   * Finds the pairs of keys a group of names with one names index id and rank gives evidence for.
   * A pair is reported once per group, with the relation of the years that says most.
   */
  private void observe(List<ExportRow> group, boolean counting, Sink sink) {
    if (counting) {
      stats.rows += group.size();
      stats.groups++;
    }
    // names without a code get the one their group agrees on
    Set<NomCode> codes = EnumSet.noneOf(NomCode.class);
    // dataset -> key id -> first name with that key
    Map<Integer, Map<Integer, ExportRow>> datasets = new TreeMap<>();
    Set<Integer> groupKeys = new HashSet<>();
    for (ExportRow row : group) {
      if (row.code() != null) {
        codes.add(row.code());
      }
      AuthorKey key = AuthorKey.of(row);
      if (key.isEmpty()) continue;
      int id = keyId(key.loose(), counting);
      groupKeys.add(id);
      datasets.computeIfAbsent(row.datasetKey(), k -> new LinkedHashMap<>()).putIfAbsent(id, row);
    }
    if (groupKeys.size() > maxKeys) {
      if (counting) stats.capped++;
      return;
    }
    final NomCode groupCode = codes.size() == 1 ? codes.iterator().next() : null;
    if (datasets.size() > 1 && counting) {
      stats.multiDataset++;
      for (int id : groupKeys) {
        freq.set(id, freq.getInt(id) + 1);
      }
    }

    Map<Long, Obs> cross = new LinkedHashMap<>();
    Map<Long, Obs> intra = new LinkedHashMap<>();
    List<Map<Integer, ExportRow>> ds = new ArrayList<>(datasets.values());
    for (int i = 0; i < ds.size(); i++) {
      // inside one dataset every two keys are names it keeps apart
      List<Map.Entry<Integer, ExportRow>> own = new ArrayList<>(ds.get(i).entrySet());
      for (int x = 0; x < own.size(); x++) {
        for (int y = x + 1; y < own.size(); y++) {
          add(intra, true, own.get(x), own.get(y), groupCode, counting);
        }
      }
      // across two datasets only what is left after removing the keys they share, and only if that is one each
      for (int j = i + 1; j < ds.size(); j++) {
        List<Map.Entry<Integer, ExportRow>> left = residual(ds.get(i), ds.get(j));
        List<Map.Entry<Integer, ExportRow>> right = residual(ds.get(j), ds.get(i));
        if (left.isEmpty() && right.isEmpty()) {
          if (counting) stats.agreeing++;
        } else if (left.size() == 1 && right.size() == 1) {
          add(cross, false, left.get(0), right.get(0), groupCode, counting);
        } else if (counting) {
          stats.ambiguous++;
        }
      }
    }
    cross.forEach((pair, o) -> sink.accept(false, pair, o.rel, o.code, o.a, o.b));
    intra.forEach((pair, o) -> sink.accept(true, pair, o.rel, o.code, o.a, o.b));
  }

  /**
   * @return the keys of a dataset with their names which the other dataset does not have
   */
  private static List<Map.Entry<Integer, ExportRow>> residual(Map<Integer, ExportRow> of, Map<Integer, ExportRow> without) {
    List<Map.Entry<Integer, ExportRow>> rows = new ArrayList<>();
    for (var e : of.entrySet()) {
      if (!without.containsKey(e.getKey())) rows.add(e);
    }
    return rows;
  }

  private void add(Map<Long, Obs> obs, boolean intra, Map.Entry<Integer, ExportRow> e1, Map.Entry<Integer, ExportRow> e2,
                   @Nullable NomCode groupCode, boolean counting) {
    final ExportRow r1 = e1.getValue();
    final ExportRow r2 = e2.getValue();
    NomCode c1 = r1.code() != null ? r1.code() : groupCode;
    NomCode c2 = r2.code() != null ? r2.code() : groupCode;
    if (c1 != null && c2 != null && c1 != c2) {
      // homonyms across codes, which production hardly ever compares
      if (counting) stats.crossCode++;
      return;
    }
    NomCode code = c1 != null ? c1 : c2;
    int id1 = e1.getKey();
    int id2 = e2.getKey();
    ExportRow a = id1 < id2 ? r1 : r2;
    ExportRow b = id1 < id2 ? r2 : r1;
    long pair = pair(code, Math.min(id1, id2), Math.max(id1, id2));
    YearRel rel = yearRel(r1, r2);
    Obs prev = obs.get(pair);
    if (prev == null || better(intra, rel, prev.rel)) {
      obs.put(pair, new Obs(rel, code, a, b));
    }
  }

  /**
   * Across datasets agreeing years say most and conflicting ones least. Inside a dataset agreeing years also come
   * first as they make the pair dubious, but a conflict says more than a missing year.
   */
  private static boolean better(boolean intra, YearRel rel, YearRel than) {
    if (intra) {
      return rank(rel) < rank(than);
    }
    return rel.ordinal() < than.ordinal();
  }

  private static int rank(YearRel intraRel) {
    return switch (intraRel) {
      case AGREE -> 0;
      case CONFLICT -> 1;
      case NONE -> 2;
    };
  }

  private void count(boolean intra, long pair, YearRel rel, @Nullable NomCode code, ExportRow a, ExportRow b) {
    int idx = pairIdx.get(pair);
    if (idx < 0) {
      idx = support.size();
      pairIdx.put(pair, idx);
      for (IntArrayList counter : List.of(support, yearAgree, yearConflict, intraYearDiff, intraNoYear, intraYearAgree)) {
        counter.add(0);
      }
    }
    if (intra) {
      inc(switch (rel) {
        case AGREE -> intraYearAgree;
        case CONFLICT -> intraYearDiff;
        case NONE -> intraNoYear;
      }, idx);
    } else if (rel == YearRel.CONFLICT) {
      inc(yearConflict, idx);
    } else {
      inc(support, idx);
      if (rel == YearRel.AGREE) {
        inc(yearAgree, idx);
      }
    }
  }

  private static void inc(IntArrayList counter, int idx) {
    counter.set(idx, counter.getInt(idx) + 1);
  }

  private Labelled[] label() {
    Labelled[] labels = new Labelled[support.size()];
    for (var e : pairIdx.long2IntEntrySet()) {
      int idx = e.getIntValue();
      PairStat stat = stat(idx, lowId(e.getLongKey()), highId(e.getLongKey()));
      Labelled l = rules.label(stat);
      labels[idx] = l;
      stats.labelled.merge(l.label(), 1, Integer::sum);
      if (l.label() != Label.UNLABELLED) {
        stats.sources.merge(l.source(), 1, Integer::sum);
      }
      if (stat.support() + stat.yearConflict() > 0) {
        stats.crossPairs++;
        stats.supportHistogram.get(bucket(stat.support())).merge(l.label(), 1, Integer::sum);
      }
      if (stat.intra() > 0) {
        stats.intraPairs++;
      }
    }
    return labels;
  }

  private static String bucket(int support) {
    if (support < 3) return SUPPORT_BUCKETS[support];
    if (support < 5) return SUPPORT_BUCKETS[3];
    if (support < 10) return SUPPORT_BUCKETS[4];
    return support < 100 ? SUPPORT_BUCKETS[5] : SUPPORT_BUCKETS[6];
  }

  private PairStat stat(int idx, int idA, int idB) {
    return new PairStat(support.getInt(idx), yearAgree.getInt(idx), yearConflict.getInt(idx), freq.getInt(idA), freq.getInt(idB),
      intraYearDiff.getInt(idx), intraNoYear.getInt(idx), intraYearAgree.getInt(idx));
  }

  /**
   * @return true if names found with the given relation are the ones to show the labelled pair with:
   * names with years wherever the label rests on years, as that is how production gets to see the pair
   */
  private boolean shows(Labelled l, int idx, boolean intra, YearRel rel) {
    return switch (l.label()) {
      case UNLABELLED -> false;
      case SAME -> !intra && (yearAgree.getInt(idx) > 0 ? rel == YearRel.AGREE : rel != YearRel.CONFLICT);
      case DIFF -> intra && (l.source() == Source.INTRA_YEARDIFF ? rel == YearRel.CONFLICT : rel == YearRel.NONE);
      case DUBIOUS -> intra && (l.source() != Source.INTRA_YEARAGREE || rel == YearRel.AGREE);
    };
  }

  private void write(TabWriter writer, Labelled l, int idx, @Nullable NomCode code, ExportRow low, ExportRow high) {
    String keyLow = AuthorKey.of(low).loose();
    String keyHigh = AuthorKey.of(high).loose();
    // side A is the key that sorts first, which unlike the ids does not depend on the order of the export
    boolean swap = keyLow.compareTo(keyHigh) > 0;
    ExportRow a = swap ? high : low;
    ExportRow b = swap ? low : high;
    PairStat stat = stat(idx, keyIds.getInt(swap ? keyHigh : keyLow), keyIds.getInt(swap ? keyLow : keyHigh));
    AuthorPair pair = new AuthorPair(l.label(), l.source(), l.weight(), stat, code, a.rank(), a.nidx(), a.scientificName(),
      swap ? keyHigh : keyLow, swap ? keyLow : keyHigh, AuthorPair.Side.of(a), AuthorPair.Side.of(b),
      AuthorPair.commonGroup(a.group(), b.group()));
    try {
      writer.write(pair.toRow());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private int keyId(String loose, boolean create) {
    int id = keyIds.getInt(loose);
    if (id < 0 && create) {
      id = keys.size();
      if (id >= 1 << ID_BITS) {
        throw new IllegalStateException("Too many keys to pack a pair into a long");
      }
      keyIds.put(loose, id);
      keys.add(loose);
      freq.add(0);
    }
    return id;
  }

  private static long pair(@Nullable NomCode code, int lowId, int highId) {
    return ((long) (code == null ? 0 : code.ordinal() + 1) << 2 * ID_BITS) | ((long) lowId << ID_BITS) | highId;
  }

  private static int lowId(long pair) {
    return (int) (pair >> ID_BITS) & ((1 << ID_BITS) - 1);
  }

  private static int highId(long pair) {
    return (int) pair & ((1 << ID_BITS) - 1);
  }

  /**
   * The year a name was established in is the one of its basionym if it has one.
   * One year apart still agrees, as sources differ by that much on the same publication all the time.
   */
  static YearRel yearRel(ExportRow r1, ExportRow r2) {
    Integer y1 = year(r1);
    Integer y2 = year(r2);
    if (y1 == null || y2 == null) {
      return YearRel.NONE;
    }
    return Math.abs(y1 - y2) <= 1 ? YearRel.AGREE : YearRel.CONFLICT;
  }

  private static Integer year(ExportRow r) {
    String year = r.basYear() != null ? r.basYear() : r.combYear();
    if (year != null) {
      Matcher m = YEAR.matcher(year);
      if (m.find()) {
        return Integer.parseInt(m.group(1));
      }
    }
    return null;
  }

  public static void main(String[] args) throws IOException {
    if (args.length != 2 || !new File(args[0]).isFile()) {
      System.err.println("Usage: AuthorPairMiner <export.tsv[.gz]> <pairs.tsv[.gz]>");
      System.exit(1);
    }
    System.out.printf("Max heap %,d MB%n", Runtime.getRuntime().maxMemory() >> 20);
    File out = new File(args[1]);
    AuthorPairMiner miner = new AuthorPairMiner(LabelRules.DEFAULT, MAX_KEYS_PER_GROUP);
    String report = miner.mine(new File(args[0]), out).render(LabelRules.DEFAULT, MAX_KEYS_PER_GROUP);
    System.out.println(report);
    Files.writeString(new File(out.getAbsoluteFile().getParentFile(), "miner-stats.txt").toPath(), report, StandardCharsets.UTF_8);
  }
}
