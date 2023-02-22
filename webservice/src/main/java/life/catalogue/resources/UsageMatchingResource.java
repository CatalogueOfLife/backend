package life.catalogue.resources;

import life.catalogue.api.jackson.PermissiveEnumSerde;
import life.catalogue.api.model.*;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.util.VocabularyUtils;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.assembly.UsageMatch;
import life.catalogue.assembly.UsageMatcherGlobal;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.common.io.CharsetDetectingStream;
import life.catalogue.common.ws.MoreMediaTypes;
import life.catalogue.csv.CsvReader;
import life.catalogue.dao.TreeStreams;
import life.catalogue.dw.jersey.filter.VaryAccept;
import life.catalogue.importer.NameInterpreter;
import life.catalogue.parser.*;

import org.apache.ibatis.session.SqlSession;

import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.univocity.parsers.common.AbstractParser;
import com.univocity.parsers.common.ParsingContext;
import com.univocity.parsers.common.ResultIterator;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

@Path("/dataset/{key}/nameusage/match")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class UsageMatchingResource {
  private static final Logger LOG = LoggerFactory.getLogger(UserResource.class);

  private final UsageMatcherGlobal matcher;
  private final NameInterpreter interpreter = new NameInterpreter(new DatasetSettings());

  public UsageMatchingResource(UsageMatcherGlobal matcher) {
    this.matcher = matcher;
  }

  static class UsageMatchWithOriginal extends UsageMatch {
    public final SimpleNameClassified<SimpleName> original;
    public final IssueContainer issues;

    public UsageMatchWithOriginal(UsageMatch match, IssueContainer issues, SimpleNameClassified<SimpleName> original) {
      super(match.datasetKey, match.usage, match.sourceDatasetKey, match.type, match.ignore, match.doubtfulUsage, match.alternatives);
      this.original = original;
      this.issues = issues;
    }
  }

  private UsageMatchWithOriginal match(int datasetKey, SimpleNameClassified<SimpleName> sn) {
    IssueContainer issues = new IssueContainer.Simple();
    return match(datasetKey, sn, issues);
  }

  private UsageMatchWithOriginal match(int datasetKey, SimpleNameClassified<SimpleName> sn, IssueContainer issues) {
    UsageMatch match;
    var opt = interpreter.interpret(sn, issues);
    if (opt.isPresent()) {
      NameUsageBase nu = (NameUsageBase) NameUsage.create(sn.getStatus(), opt.get().getName());
      match = matcher.match(datasetKey, nu, sn.getClassification());
    } else {
      match = UsageMatch.empty(0);
      issues.addIssue(Issue.UNPARSABLE_NAME);
    }
    return new UsageMatchWithOriginal(match, issues, sn);
  }


  @GET
  public UsageMatchWithOriginal match(@PathParam("key") int datasetKey,
                                      @QueryParam("id") String id,
                                      @QueryParam("q") String q,
                                      @QueryParam("name") String name,
                                      @QueryParam("scientificName") String sciname,
                                      @QueryParam("authorship") String authorship,
                                      @QueryParam("code") NomCode code,
                                      @QueryParam("rank") Rank rank,
                                      @QueryParam("status") @DefaultValue("ACCEPTED") TaxonomicStatus status,
                                      @BeanParam Classification classification
  ) throws InterruptedException {
    if (status == TaxonomicStatus.BARE_NAME) {
      throw new IllegalArgumentException("Cannot match a bare name to a name usage");
    }
    SimpleNameClassified<SimpleName> orig = SimpleNameClassified.snc(id, rank, code, status, ObjectUtils.coalesce(sciname, name, q), authorship);
    if (classification != null) {
      orig.setClassification(classification.asSimpleNames());
    }
    return match(datasetKey, orig);
  }

  @POST
  @Path("source/{key2}")
  @Produces({MoreMediaTypes.TEXT_CSV, MoreMediaTypes.TEXT_TSV})
  public Stream<Object[]> matchSourceDataset(@PathParam("key") int key1,
                                             @PathParam("key2") int key2,
                                             // source key2 parameters
                                             @QueryParam("taxonID") String taxonID,
                                             @QueryParam("rank") Rank rank,
                                             @QueryParam("synonyms") boolean synonyms,
                                             @QueryParam("extinct") Boolean extinct,
                                             @Context SqlSession session
  ) throws IOException {
    var stream = TreeStreams.dataset(session, key2, synonyms, extinct, taxonID, rank);
    return match2Rows(stream.map(snc -> match(key1, snc)));
  }

  @POST
  @VaryAccept
  @Consumes(MediaType.APPLICATION_JSON)
  public List<UsageMatchWithOriginal> matchList(@PathParam("key") int datasetKey, List<SimpleNameClassified<SimpleName>> names) {
    List<UsageMatchWithOriginal> usages = new ArrayList<>(names.size());
    for (SimpleNameClassified<SimpleName> sn : names) {
      usages.add(match(datasetKey, sn));
    }
    return usages;
  }

  @POST
  @VaryAccept
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes({MoreMediaTypes.TEXT_CSV})
  public Stream<UsageMatchWithOriginal> matchCSV2JSON(@PathParam("key") int datasetKey, InputStream data) throws IOException {
    return matchData(datasetKey, data, CsvReader.newParser(CsvReader.csvSetting()));
  }

  @POST
  @VaryAccept
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes({MediaType.TEXT_PLAIN, MoreMediaTypes.TEXT_TSV})
  public Stream<UsageMatchWithOriginal> matchTSV2JSON(@PathParam("key") int datasetKey, InputStream data) throws IOException {
    return matchData(datasetKey, data, CsvReader.newParser(CsvReader.tsvSetting()));
  }

  @POST
  @VaryAccept
  @Produces({MoreMediaTypes.TEXT_CSV, MoreMediaTypes.TEXT_TSV})
  @Consumes({MoreMediaTypes.TEXT_CSV})
  public Stream<Object[]> matchCSV(@PathParam("key") int datasetKey, InputStream data) throws IOException {
    return match2Rows(matchData(datasetKey, data, CsvReader.newParser(CsvReader.csvSetting())));
  }

  @POST
  @VaryAccept
  @Produces({MoreMediaTypes.TEXT_CSV, MoreMediaTypes.TEXT_TSV})
  @Consumes({MediaType.TEXT_PLAIN, MoreMediaTypes.TEXT_TSV})
  public Stream<Object[]> matchTSV(@PathParam("key") int datasetKey, InputStream data) throws IOException {
    return match2Rows(matchData(datasetKey, data, CsvReader.newParser(CsvReader.tsvSetting())));
  }

  private Stream<Object[]> match2Rows(Stream<UsageMatchWithOriginal> data) throws IOException {
    return Stream.concat(
      Stream.ofNullable(new Object[]{
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
        "parent",
        "classification"
      }),
      data.map(m -> {
        Object[] row = new Object[12];
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
          row[10] = m.usage.getParent();
          row[11] = str(m.usage.getClassification());
        }
        return row;
      }));
  }

  private Stream<UsageMatchWithOriginal> matchData(int datasetKey, InputStream data, AbstractParser<?> parser) throws IOException {
    BufferedReader reader  = CharsetDetectingStream.createReader(data);
    parser.beginParsing(reader);
    ResultIterator<String[], ParsingContext> iter = parser.iterate(reader).iterator();
    final RowMapper mapper = new RowMapper(iter.next());

    Stream<String[]> rowStream = StreamSupport.stream(Spliterators.spliteratorUnknownSize(iter, Spliterator.ORDERED), false);
    return rowStream.map(row -> {
      final IssueContainer issues = new IssueContainer.Simple();
      return match(datasetKey, mapper.build(row, issues), issues);
    });
  }

  static String str(Enum<?> val) {
    return val == null ? null : PermissiveEnumSerde.enumValueName(val);
  }

  static String str(List<SimpleNameWithPub> classification) {
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
