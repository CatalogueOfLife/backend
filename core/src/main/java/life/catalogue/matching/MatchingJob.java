package life.catalogue.matching;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.jackson.PermissiveEnumSerde;
import life.catalogue.api.model.*;
import life.catalogue.api.util.VocabularyUtils;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.TabularFormat;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.common.io.CharsetDetectingStream;
import life.catalogue.common.io.TabReader;
import life.catalogue.common.io.TempFile;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.DatasetBlockingJob;
import life.catalogue.concurrent.JobPriority;
import life.catalogue.concurrent.UsageCounter;
import life.catalogue.config.NormalizerConfig;
import life.catalogue.csv.CsvReader;
import life.catalogue.dao.TreeStreams;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.TaxonMapper;
import life.catalogue.interpreter.NameInterpreter;
import life.catalogue.parser.*;

import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.Term;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import org.gbif.nameparser.api.Rank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.univocity.parsers.common.AbstractParser;
import com.univocity.parsers.common.AbstractWriter;
import com.univocity.parsers.common.ParsingContext;
import com.univocity.parsers.common.ResultIterator;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;
import com.univocity.parsers.tsv.TsvWriter;
import com.univocity.parsers.tsv.TsvWriterSettings;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

/**
 * Matching job for users that does not the power to insert names into the names index.
 * Rematching of datasets is done by the DatasetMatcher instead.
 */
public class MatchingJob extends DatasetBlockingJob {
  private static final Logger LOG = LoggerFactory.getLogger(MatchingJob.class);
  private static final String CUSTOM_COL_PREFIX = "original_";
  private static final CsvWriterSettings CSV = new CsvWriterSettings();
  static {
    CSV.setQuotationTriggers('"', ',');
  }

  private final SqlSessionFactory factory;
  private final UsageMatcherFactory matcherFactory;
  private UsageMatcher matcher;
  private final MatchingUtils utils;
  private final NameInterpreter interpreter = new NameInterpreter(new DatasetSettings(), true);
  private final NormalizerConfig cfg;
  // job specifics
  private final MatchingRequest req;
  private final JobResult result;
  private final UsageCounter counter = new UsageCounter();
  private List<SimpleName> rootClassification;

  public MatchingJob(MatchingRequest req, int userKey, SqlSessionFactory factory, UsageMatcherFactory matcherFactory, NormalizerConfig cfg) {
    super(req.getDatasetKey(), userKey, JobPriority.LOW);
    this.logToFile = true;
    this.cfg = cfg;
    this.factory = factory;
    this.matcherFactory = matcherFactory;
    this.utils = new MatchingUtils(matcherFactory.getNameIndex());
    this.req = Preconditions.checkNotNull(req);
    this.result = new JobResult(getKey());
    this.dataset = loadDataset(factory, req.getDatasetKey());
    if (req.getTaxonID() != null) {
      final var key = DSID.of(req.getDatasetKey(), req.getTaxonID());
      try (SqlSession session = factory.openSession(true)) {
        this.rootClassification = session.getMapper(TaxonMapper.class).classificationSimple(key);
        if (rootClassification == null || rootClassification.isEmpty()) {
          // make sure root does exist
          SimpleName root = session.getMapper(NameUsageMapper.class).getSimple(key);
          if (root == null) {
            throw new NotFoundException("Root taxon " + req.getTaxonID() + " does not exist in dataset " + req.getDatasetKey());
          }
        }
      }
    }
  }

  public JobResult getResult() {
    return result;
  }

  public MatchingRequest getRequest() {
    return req;
  }

  private File matchResultFile() {
    return cfg.scratchFile(getKey());
  }

  @Override
  public String getEmailTemplatePrefix() {
    return "matching";
  }

  @Override
  public boolean isDuplicate(BackgroundJob other) {
    if (other instanceof MatchingJob) {
      var mj = (MatchingJob) other;
      return req.equals(mj.req);
    }
    return super.isDuplicate(other);
  }

  @Override
  public final void runWithLock() throws Exception {
    matcher = matcherFactory.persistent(req.getDatasetKey());
    try (TempFile tmp = TempFile.created(matchResultFile())) {
      try (ZipOutputStream zos = UTF8IoUtils.zipStreamFromFile(tmp.file)) {

        LOG.info("Write matches for job {} to temp file {}", getKey(), tmp.file.getAbsolutePath());
        zos.putNextEntry(new ZipEntry(req.resultFileName()));

        AbstractWriter<?> writer = req.getFormat() == TabularFormat.CSV ?
                                   new CsvWriter(zos, StandardCharsets.UTF_8, CSV) :
                                   new TsvWriter(zos, StandardCharsets.UTF_8, new TsvWriterSettings());
        // match
        if (req.getUpload() != null) {
          LOG.info("Match uploaded names from {} file {}", req.getFormat(), req.getUpload());
          var mstream = streamUpload();
          writeMatches(writer, mstream.mapper.rawHeader, mstream.stream);
          // delete file upload
          FileUtils.deleteQuietly(req.getUpload());

        } else if (req.getSourceDatasetKey() != null) {
          try (SqlSession session = factory.openSession()) {
            // we need to swap datasetKey for sourceDatasetKey - we dont want to traverse and match the target!
            final TreeTraversalParameter ttp = new TreeTraversalParameter(req);
            ttp.setDatasetKey(req.getSourceDatasetKey());
            final AtomicLong count = new AtomicLong(0);
            writeMatches(writer, null, TreeStreams.dataset(session, ttp)
                                            .map(sn -> {
                                              if (rootClassification != null) {
                                                sn.getClassification().addAll(rootClassification);
                                              }
                                              return new IssueName(sn, new IssueContainer.Simple(), null, count.incrementAndGet());
                                            })
            );
          }

        } else {
          throw new IllegalArgumentException("Upload or sourceDatasetKey required");
        }
        writer.flush();
        zos.closeEntry();
      }
      // move to final result file
      FileUtils.copyFile(tmp.file, result.getFile());
      result.calculateSizeAndMd5();
      LOG.info("Matching {} with {} usages to dataset {} completed: {} [{}]", getKey(), counter.size(), datasetKey, result.getFile(), result.getSizeWithUnit());
    }
  }

  static class IssueName {
    final SimpleNameClassified<SimpleName> name;
    final IssueContainer issues;
    final long line;
    final String[] row;

    IssueName(SimpleNameClassified<SimpleName> name, IssueContainer issues, String[] row, long line) {
      this.issues = issues;
      this.name = name;
      this.line = line;
      this.row = row;
    }
  }

  private void writeMatches(AbstractWriter<?> writer, String[] srcHeader, Stream<IssueName> names) {
    AtomicLong counter = new AtomicLong(0);
    AtomicLong none = new AtomicLong(0);

    try (names) {
      // write header
      List<String> cols = new ArrayList<>();
      // first the original columns, prefixed by "original_"
      if (srcHeader != null) {
        for (var h : srcHeader) {
          cols.add(CUSTOM_COL_PREFIX + StringUtils.stripToEmpty(h));
        }
      }
      final int firstColIdx = cols.size();
      cols.addAll(List.of(
        "matchType",
        "matchIssues",
        "ID",
        "rank",
        "scientificName",
        "authorship",
        "status",
        "acceptedID",
        "acceptedScientificName",
        "acceptedAuthorship",
        "kingdom",
        "phylum",
        "class",
        "order",
        "family",
        "genus",
        "classification"
      ));
      writer.writeHeaders(cols);
      final int size = cols.size();

      // match & write to file
      names.forEach(n -> {
          var m = match(n);
          var row = new String[size];
          // first add all original input columns if provided (only works with file uploads)
          if (srcHeader != null && n.row != null) {
            int idx = 0;
            for (String val : n.row) {
              // make sure we dont have more columns than headers
              if (idx < firstColIdx) {
                row[idx] = val;
              }
              idx++;
            }
          }

          row[firstColIdx] = str(m.type);
          row[firstColIdx+1] = concat(m.issues);
          if (m.usage != null) {
              row[firstColIdx+2] = m.usage.getId();
              row[firstColIdx+3] = str(m.usage.getRank());
              row[firstColIdx+4] = m.usage.getName();
              row[firstColIdx+5] = m.usage.getAuthorship();
              row[firstColIdx+6] = str(m.usage.getStatus());
              if (m.usage.getStatus().isSynonym() && !m.usage.getClassification().isEmpty()) {
                var acc = m.usage.getClassification().get(0);
                row[firstColIdx+7] = acc.getId();
                row[firstColIdx+8] = acc.getName();
                row[firstColIdx+9] = acc.getAuthorship();
              } else {
                row[firstColIdx+7] = null;
                row[firstColIdx+8] = null;
                row[firstColIdx+9] = null;
              }
              Classification cl = new Classification(m.usage.getClassification());
              row[firstColIdx+10] = cl.getKingdom();
              row[firstColIdx+11] = cl.getPhylum();
              row[firstColIdx+12] = cl.getClass_();
              row[firstColIdx+13] = cl.getOrder();
              row[firstColIdx+14] = cl.getFamily();
              row[firstColIdx+15] = cl.getGenus();
              row[firstColIdx+16] = str(m.usage.getClassification());
          } else {
              none.incrementAndGet();
          }
          writer.writeRow(row);
          if (counter.incrementAndGet() % 10_000 == 0) {
              LOG.debug("Matched {} out of {} names so far", counter.get() - none.get(), counter);
          }
      });

    } catch (Exception e) {
      LOG.error("Matching failed on line #{}: {}", counter.get()+1, e.getMessage(), e);
    }
    writer.flush();
    LOG.info("Matched {} out of {} names", counter.get()-none.get(), counter);
  }

  private UsageMatchWithOriginal match(IssueName n) {
    UsageMatch match = interpretAndMatch(n.name, MatchingUtils.toSimpleNameCached(n.name.getClassification()), n.issues, false, interpreter, utils, matcher);
    return new UsageMatchWithOriginal(match, n.issues, n.name, n.line);
  }

  public static UsageMatch interpretAndMatch(SimpleName sn, List<SimpleNameCached> classification, IssueContainer issues, boolean verbose,
                                       NameInterpreter interpreter, MatchingUtils utils, UsageMatcher matcher
  ) {
    UsageMatch match;
    var opt = interpreter.interpret(sn, issues);
      if (opt.isPresent()) {
      NameUsageBase nu = (NameUsageBase) NameUsage.create(sn.getStatus(), opt.get().getName());
      // replace name parsers unranked with null to let the matcher know its coming from outside
      if (nu.getRank() == Rank.UNRANKED) {
        nu.getName().setRank(null);
      }
      var snc = utils.toSimpleNameClassified(nu, classification);
      match = matcher.match(snc, false, verbose);
    } else {
      match = UsageMatch.empty(0);
      issues.add(Issue.UNPARSABLE_NAME);
    }
      return match;
  }

  private static class MappedStream {
    final RowMapper mapper;
    final Stream<IssueName> stream;

    private MappedStream(RowMapper mapper, Stream<IssueName> stream) {
      this.mapper = mapper;
      this.stream = stream;
    }
  }
  private MappedStream streamUpload() throws IOException {
    final InputStream data = new FileInputStream(req.getUpload());
    final AbstractParser<?> parser = req.getFormat() == TabularFormat.CSV ?
                               TabReader.newParser(CsvReader.csvSetting()) :
                               TabReader.newParser(CsvReader.tsvSetting());

    BufferedReader reader  = CharsetDetectingStream.createReader(data);
    parser.beginParsing(reader);
    ResultIterator<String[], ParsingContext> iter = parser.iterate(reader).iterator();
    final RowMapper mapper = new RowMapper(iter.next());

    Stream<String[]> rowStream = StreamSupport.stream(Spliterators.spliteratorUnknownSize(iter, Spliterator.ORDERED), false);

    return new MappedStream(mapper, rowStream.map(row -> {
      final IssueContainer issues = new IssueContainer.Simple();
      long line = iter.getContext().currentLine();
      try {
        return new IssueName(mapper.build(row, issues), issues, row, line);
      } catch (Exception e) {
        LOG.error("Error parsing line {}", line, e);
        issues.add(Issue.NOT_INTERPRETED);
        return new IssueName(null, issues, row, line);
      }
    }));
  }

  static String str(Enum<?> val) {
    return val == null ? null : PermissiveEnumSerde.enumValueName(val);
  }

  static String str(List<SimpleNameCached> classification) {
    StringBuilder sb = new StringBuilder();
    for (var sn : classification) {
      if (sb.length()>1) {
        sb.append("|");
      }
      sb.append(sn.getRank());
      sb.append(":");
      sb.append(sn.getLabel());
    }
    return sb.toString();
  }

  static String concat(IssueContainer issues) {
    if (issues != null) {
      StringBuilder sb = new StringBuilder();
      for (var iss : issues.getIssues()) {
        if (sb.length()>1) {
          sb.append(";");
        }
        sb.append(iss);
      }
      return sb.toString();
    }
    return null;
  }

  static class RowMapper {
    final Object2IntMap<Term> header;
    final String[] rawHeader;

    public RowMapper(String[] header) {
      if (header == null) {
        throw new IllegalArgumentException("Header columns required. Failed to read matching input");
      }
      this.rawHeader = header;
      this.header = new Object2IntOpenHashMap<>();
      int idx = 0;
      for (String h : header) {
        var opt = VocabularyUtils.findTerm(ColdpTerm.ID.prefix(), h, false);
        if (opt.isPresent()){
          this.header.put(opt.get(), idx);
        }
        idx++;
      }
      if (!this.header.containsKey(ColdpTerm.scientificName)) {
        throw new IllegalArgumentException("scientificName column required");
      }
    }

    String val(Term t, String[] row) {
      return header.containsKey(t) ? row[header.getInt(t)] : null;
    }

    <T extends Enum<?>> T parse(Term t, String[] row, Parser<T> parser, Issue unparsableIssue, IssueContainer issues) {
      String val = val(t, row);
      return SafeParser.parse(parser, val).orNull(unparsableIssue, issues);
    }

    <T extends Enum<?>> T parse(Term t, String[] row, EnumNoteParser<T> parser, Issue unparsableIssue, IssueContainer issues) {
      String val = val(t, row);
      var note = SafeParser.parse(parser, val).orNull(unparsableIssue, issues);
      return note == null ? null : note.val;
    }

    SimpleNameClassified<SimpleName> build(String[] row, IssueContainer issues) {
      final SimpleNameClassified<SimpleName> sn = new SimpleNameClassified<>();
      sn.setId(val(ColdpTerm.ID, row));
      sn.setName(val(ColdpTerm.scientificName, row));
      sn.setAuthorship(val(ColdpTerm.authorship, row));
      sn.setRank(parse(ColdpTerm.rank, row, RankParser.PARSER, Issue.RANK_INVALID, issues));
      sn.setCode(parse(ColdpTerm.code, row, NomCodeParser.PARSER, Issue.NOMENCLATURAL_CODE_INVALID, issues));
      sn.setStatus(parse(ColdpTerm.status, row, TaxonomicStatusParser.PARSER, Issue.TAXONOMIC_STATUS_INVALID, issues));

      Classification cl = new Classification();
      for (var entry : header.object2IntEntrySet()) {
        Term t = entry.getKey();
        if (t instanceof ColdpTerm) {
          ColdpTerm ct = (ColdpTerm) t;
          cl.setByTerm(ct, row[entry.getIntValue()]);
        } else if (t instanceof DwcTerm) {
          DwcTerm dt = (DwcTerm) t;
          cl.setByTerm(dt, row[entry.getIntValue()]);
        }
      }
      sn.setClassification(cl.asSimpleNames());

      return sn;
    }

  }

}
