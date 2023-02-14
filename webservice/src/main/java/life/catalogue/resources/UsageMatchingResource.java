package life.catalogue.resources;

import com.univocity.parsers.common.AbstractParser;

import com.univocity.parsers.common.ParsingContext;

import com.univocity.parsers.common.ResultIterator;

import it.unimi.dsi.fastutil.objects.Object2IntMap;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.jackson.PermissiveEnumSerde;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.assembly.UsageMatch;
import life.catalogue.assembly.UsageMatcherGlobal;

import life.catalogue.common.io.CharsetDetectingStream;
import life.catalogue.common.ws.MoreMediaTypes;
import life.catalogue.csv.CsvReader;

import org.gbif.dwc.terms.Term;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Path("/dataset/{key}/nameusage/match")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class UsageMatchingResource {
  private static final Logger LOG = LoggerFactory.getLogger(UserResource.class);

  private final UsageMatcherGlobal matcher;

  public UsageMatchingResource(UsageMatcherGlobal matcher) {
    this.matcher = matcher;
  }

  @GET
  public UsageMatch match(@PathParam("key") int datasetKey,
                                         @QueryParam("name") String name,
                                         @QueryParam("authorship") String authorship,
                                         @QueryParam("code") NomCode code,
                                         @QueryParam("rank") Rank rank,
                                         @QueryParam("status") @DefaultValue("ACCEPTED") TaxonomicStatus status,
                                         @BeanParam Classification classification
  ) throws InterruptedException {
    if (status == TaxonomicStatus.BARE_NAME) {
      throw new IllegalArgumentException("Cannot match a bare name to a name usage");
    }

    Name n = NamesIndexResource.name(name, authorship, rank, code);
    NameUsageBase nu = (NameUsageBase) NameUsage.create(status, n);
    return matcher.match(datasetKey, nu, classification);
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public List<UsageMatch> matchList(@PathParam("key") int datasetKey, List<SimpleNameClassified> names) {
    // safeguard
    if (names.size() > 100_000) {
      throw new IllegalArgumentException("Matching is restricted to 100.000 names");
    }
    List<UsageMatch> usages = new ArrayList<>(names.size());
    for (SimpleNameClassified sn : names) {
      Name n = new Name(sn);
      NameUsageBase nu = (NameUsageBase) NameUsage.create(sn.getStatus(), n);
      usages.add(matcher.match(datasetKey, nu, sn.getClassification()));
    }
    return usages;
  }

  @Produces({MoreMediaTypes.TEXT_CSV, MoreMediaTypes.TEXT_TSV})
  @Consumes({MoreMediaTypes.TEXT_CSV})
  public Stream<Object[]> matchCSV(@PathParam("key") int datasetKey, InputStream data) throws IOException {
    return matchAll(datasetKey, data, CsvReader.newParser(CsvReader.csvSetting()));
  }

  @Produces({MoreMediaTypes.TEXT_CSV, MoreMediaTypes.TEXT_TSV})
  @Consumes({MediaType.TEXT_PLAIN, MoreMediaTypes.TEXT_TSV})
  public Stream<Object[]> matchTSV(@PathParam("key") int datasetKey, InputStream data) throws IOException {
    return matchAll(datasetKey, data, CsvReader.newParser(CsvReader.tsvSetting()));
  }

  private Stream<Object[]> matchAll(int datasetKey, InputStream data, AbstractParser<?> parser) throws IOException {
    AtomicInteger counter = new AtomicInteger();
    try (CharsetDetectingStream in = CharsetDetectingStream.create(data)) {
      final Charset charset = in.getCharset();
      LOG.info("Use encoding {} for input names", charset);

      Reader reader = CharsetDetectingStream.createReader(data, charset);
      parser.beginParsing(reader);
      ResultIterator<String[], ParsingContext> iter = parser.iterate(reader).iterator();
      final RowMapper mapper = new RowMapper(iter.next());

      Stream<String[]> rowStream = StreamSupport.stream(Spliterators.spliteratorUnknownSize(iter, Spliterator.ORDERED), false);
      return Stream.concat(
        Stream.of(new Object[][]{new Object[]{
          "inputID",
          "inputRank",
          "inputName",
          "matchType",
          "ID",
          "rank",
          "label",
          "name",
          "authorship",
          "status",
          "parent",
          "classification"
        }}),
        rowStream.map(row -> {
          NameUsageBase nu = mapper.buildUsage(row);
          counter.incrementAndGet();
          var m = matcher.match(datasetKey, nu, mapper.buildClassification(row));
          return new Object[]{
            nu.getId(),
            str(nu.getRank()),
            nu.getLabel(),
            str(m.type),
            m.usage.getId(),
            str(m.usage.getRank()),
            m.usage.getLabel(),
            m.usage.getName(),
            m.usage.getAuthorship(),
            str(m.usage.getStatus()),
            m.usage.getParent(),
            str(m.usage.getClassification())
          };
        })
      );
    }
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
      this.header = new Object2IntOpenHashMap<>();
    }

    NameUsageBase buildUsage(String[] row) {
      //TODO
      return null;
    }

    List<SimpleName> buildClassification(String[] row) {
      //TODO
      return null;
    }

  }
}
