package life.catalogue.matching;

import life.catalogue.WsServerConfig;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.exception.UnavailableException;
import life.catalogue.api.jackson.PermissiveEnumSerde;
import life.catalogue.api.model.*;
import life.catalogue.api.util.VocabularyUtils;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.TabularFormat;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.common.io.CharsetDetectingStream;
import life.catalogue.common.io.TempFile;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.concurrent.DatasetBlockingJob;
import life.catalogue.concurrent.JobPriority;
import life.catalogue.concurrent.UsageCounter;
import life.catalogue.csv.CsvReader;
import life.catalogue.dao.TreeStreams;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.TaxonMapper;
import life.catalogue.importer.NameInterpreter;
import life.catalogue.parser.*;

import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.Term;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
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

public class MatchingJob extends DatasetBlockingJob {
  private static final Logger LOG = LoggerFactory.getLogger(MatchingJob.class);
  private final SqlSessionFactory factory;
  private final UsageMatcherGlobal matcher;
  private final NameInterpreter interpreter = new NameInterpreter(new DatasetSettings(), true);
  private final WsServerConfig cfg;
  // job specifics
  private final MatchingRequest req;
  private final JobResult result;
  private final UsageCounter counter = new UsageCounter();
  private List<SimpleName> rootClassification;

  public MatchingJob(MatchingRequest req, int userKey, SqlSessionFactory factory, UsageMatcherGlobal matcher, WsServerConfig cfg) {
    super(req.getDatasetKey(), userKey, JobPriority.LOW);
    this.cfg = cfg;
    this.matcher = matcher;
    this.factory = factory;
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
    return cfg.normalizer.scratchFile(getKey());
  }

  @Override
  public String getEmailTemplatePrefix() {
    return "matching";
  }

  @Override
  public void assertComponentsOnline() throws UnavailableException {
    matcher.assertComponentsOnline();
  }

  @Override
  public final void runWithLock() throws Exception {
    try (TempFile tmp = TempFile.created(matchResultFile())) {
      try (ZipOutputStream zos = UTF8IoUtils.zipStreamFromFile(tmp.file)) {

        LOG.info("Write matches for job {} to temp file {}", getKey(), tmp.file.getAbsolutePath());
        zos.putNextEntry(new ZipEntry(req.resultFileName()));

        AbstractWriter<?> writer = req.getFormat() == TabularFormat.CSV ?
                                   new CsvWriter(zos, StandardCharsets.UTF_8, new CsvWriterSettings()) :
                                   new TsvWriter(zos, StandardCharsets.UTF_8, new TsvWriterSettings());
        // match
        if (req.getUpload() != null) {
          LOG.info("Match uploaded names from {}", req.getUpload());
          writeMatches(writer, streamUpload());
          // delete file upload
          FileUtils.deleteQuietly(req.getUpload());

        } else if (req.getSourceDatasetKey() != null) {
          try (SqlSession session = factory.openSession()) {
            // we need to swap datasetKey for sourceDatasetKey - we dont want to traverse and match the target!
            final TreeTraversalParameter ttp = new TreeTraversalParameter(req);
            ttp.setDatasetKey(req.getSourceDatasetKey());
            writeMatches(writer, TreeStreams.dataset(session, ttp)
                                            .map(sn -> {
                                              if (rootClassification != null) {
                                                sn.getClassification().addAll(rootClassification);
                                              }
                                              return new IssueName(sn, new IssueContainer.Simple());
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

    IssueName(SimpleNameClassified<SimpleName> name, IssueContainer issues) {
      this.issues = issues;
      this.name = name;
    }
  }

  private void writeMatches(AbstractWriter<?> writer, Stream<IssueName> names) throws IOException {
    // write header
    writer.writeHeaders(
      "inputID",
      "inputRank",
      "inputName",

      "matchType",
      "ID",
      "rank",
      "label",
      "scientificName",
      "authorship",
      "status",
      "acceptedName",
      "classification",
      "issues"
    );

    // match & write to file
    AtomicLong counter = new AtomicLong(0);
    AtomicLong none = new AtomicLong(0);
    names.map(this::match).forEach(m -> {
      var row = new String[13];
      row[0] = m.original.getId();
      row[1] = str(m.original.getRank());
      row[2] = m.original.getLabel();
      row[3] = str(m.type);
      if (m.usage != null) {
        row[4] = m.usage.getId();
        row[5] = str(m.usage.getRank());
        row[6] = m.usage.getLabel();
        row[7] = m.usage.getName();
        row[8] = m.usage.getAuthorship();
        row[9] = str(m.usage.getStatus());
        if (m.usage.getStatus().isSynonym() && !m.usage.getClassification().isEmpty()) {
          row[10] = m.usage.getClassification().get(0).getLabel();
        } else {
          row[10] = null;
        }
        row[11] = str(m.usage.getClassification());
        row[12] = concat(m.issues);
      } else {
        none.incrementAndGet();
      }
      writer.writeRow(row);
      if (counter.incrementAndGet() % 100 == 0) {
        LOG.debug("Matched {} out of {} names so far", counter.get()-none.get(), counter);
      }
    });
    writer.flush();
    LOG.info("Matched {} out of {} names", counter.get()-none.get(), counter);
  }

  private UsageMatchWithOriginal match(IssueName n) {
    UsageMatch match;
    var opt = interpreter.interpret(n.name, n.issues);
    if (opt.isPresent()) {
      NameUsageBase nu = (NameUsageBase) NameUsage.create(n.name.getStatus(), opt.get().getName());
      match = matcher.match(datasetKey, nu, n.name.getClassification());
    } else {
      match = UsageMatch.empty(0);
      n.issues.addIssue(Issue.UNPARSABLE_NAME);
    }
    return new UsageMatchWithOriginal(match, n.issues, n.name);
  }

  private Stream<IssueName> streamUpload() throws IOException {
    final InputStream data = new FileInputStream(req.getUpload());
    final AbstractParser<?> parser = req.getUpload().getName().endsWith("csv") ?
                               CsvReader.newParser(CsvReader.csvSetting()) :
                               CsvReader.newParser(CsvReader.tsvSetting());

    BufferedReader reader  = CharsetDetectingStream.createReader(data);
    parser.beginParsing(reader);
    ResultIterator<String[], ParsingContext> iter = parser.iterate(reader).iterator();
    final RowMapper mapper = new RowMapper(iter.next());

    Stream<String[]> rowStream = StreamSupport.stream(Spliterators.spliteratorUnknownSize(iter, Spliterator.ORDERED), false);
    return rowStream.map(row -> {
      final IssueContainer issues = new IssueContainer.Simple();
      return new IssueName(mapper.build(row, issues), issues);
    });
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
    StringBuilder sb = new StringBuilder();
    for (var iss : issues.getIssues()) {
      if (sb.length()>1) {
        sb.append(";");
      }
      sb.append(iss);
    }
    return sb.toString();
  }

  static class RowMapper {
    final Object2IntMap<Term> header;

    public RowMapper(String[] header) {
      if (header == null) {
        throw new IllegalArgumentException("Header columns required. Failed to read matching input");
      }
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
