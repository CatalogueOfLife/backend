package life.catalogue.resources.parser;

import life.catalogue.api.model.IssueContainer;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.ParsedNameUsage;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.Issue;
import life.catalogue.parser.NameParser;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.ParseResult;
import org.gbif.nameparser.api.Rank;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/parser/name")
@Produces(MediaType.APPLICATION_JSON)
public class NameParserResource {

  private static final Logger LOG = LoggerFactory.getLogger(NameParserResource.class);
  private static final NameParser parser = NameParser.PARSER;

  public static class CRName implements IssueContainer {
    private NomCode code;
    private Rank rank;
    private String name;
    private String authorship;
    private Set<Issue> issues = EnumSet.noneOf(Issue.class);

    public CRName() {
    }

    public CRName(NomCode code, Rank rank, String name, String authorship) {
      this.code = code;
      this.rank = rank;
      this.name = name;
      this.authorship = authorship;
    }

    public Rank getRank() {
      return rank;
    }

    public void setRank(Rank rank) {
      this.rank = rank;
    }

    public NomCode getCode() {
      return code;
    }

    public void setCode(NomCode code) {
      this.code = code;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getAuthorship() {
      return authorship;
    }

    public void setAuthorship(String authorship) {
      this.authorship = authorship;
    }

    @Override
    public Set<Issue> getIssues() {
      return issues;
    }

    @Override
    public void setIssues(Set<Issue> issues) {
      this.issues = issues;
    }

    @Override
    public boolean remove(Issue issue) {
      return issues.remove(issue);
    }

    @Override
    public boolean contains(Issue issue) {
      return issues.contains(issue);
    }
  }

  /**
   * For name parsing results only.
   * We flatten results here to just a single json object without a usage with a nested name.
   * And add issues.
   */
  public static class PNIssue extends Name {
    public final boolean extinct;
    public final String taxonomicNote;
    public final String publishedIn;
    public final Set<Issue> issues;

    public PNIssue(ParsedNameUsage pnu, Set<Issue> issues) {
      super(pnu.getName());
      this.extinct = pnu.isExtinct();
      this.taxonomicNote = pnu.getTaxonomicNote();
      this.publishedIn = pnu.getPublishedIn();
      this.issues = issues;
    }
  }


  /**
   * Parsing names as GET query parameters.
   */
  @GET
  public Optional<PNIssue> parseGet(@QueryParam("code") NomCode code,
                                    @QueryParam("rank") Rank rank,
                                    @QueryParam("q") String name,
                                    @QueryParam("name") String name2, // legacy parameter
                                    @QueryParam("authorship") String authorship) {
    return parse(new CRName(code, rank, ObjectUtils.coalesce(name, name2), authorship));
  }

  /**
   * Parsing names as a json array.
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public List<PNIssue> parseJson(List<CRName> names) {
    return parse(names.stream());
  }
  
  /**
   * Parsing names by uploading a plain UTF-8 text file using one line per scientific name.
   * <pre>
   * curl -F names=@scientific_names.txt http://api.checklistbank.org/parser/name
   * </pre>
   */
  @POST
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public List<PNIssue> parseFile(@FormDataParam("code") NomCode code,
                                 @FormDataParam("rank") Rank rank,
                                 @FormDataParam("names") InputStream file) {
    return parse(lines(code, rank, file));
  }

  /**
   * Parsing names by posting plain text content using one line per scientific name.
   * Make sure to preserve new lines (\n) in the posted data, for example use --data-binary with curl:
   * <pre>
   * curl POST -H "Content-Type:text/plain" --data-binary @scientific_names.txt http://api.checklistbank.org/parser/name
   * </pre>
   */
  @POST
  @Consumes(MediaType.TEXT_PLAIN)
  public List<PNIssue> parsePlainText(@QueryParam("code") NomCode code, @QueryParam("rank") Rank rank, InputStream names) {
    return parseFile(code, rank, names);
  }

  /**
   * Parses a single name with the GBIF name parser and returns its raw result without any CLB interpretation:
   * no issues, no {@link Name} built. The "result" property tells a parsed, informal and unparsable name apart.
   */
  @GET
  @Path("native")
  public ParseResult parseNative(@QueryParam("code") NomCode code,
                                 @QueryParam("rank") Rank rank,
                                 @QueryParam("q") String name,
                                 @QueryParam("authorship") String authorship) {
    if (StringUtils.isBlank(name)) {
      throw new IllegalArgumentException("Name parameter q required");
    }
    return parseNative(new CRName(code, rank, name, authorship));
  }

  /**
   * Raw GBIF name parser results for plain text content using one line per scientific name, see {@link #parseNative}.
   * Blank lines are skipped. Make sure to preserve new lines (\n) in the posted data, for example use --data-binary with curl:
   * <pre>
   * curl POST -H "Content-Type:text/plain" --data-binary @scientific_names.txt http://api.checklistbank.org/parser/name/native
   * </pre>
   */
  @POST
  @Path("native")
  @Consumes(MediaType.TEXT_PLAIN)
  public List<ParseResult> parseNativePlainText(@QueryParam("code") NomCode code, @QueryParam("rank") Rank rank, InputStream names) {
    return lines(code, rank, names)
        .map(this::parseNative)
        .collect(Collectors.toList());
  }

  /**
   * @return the non blank lines of a UTF-8 text stream as names to parse
   */
  private static Stream<CRName> lines(NomCode code, Rank rank, InputStream data) {
    if (data == null) {
      throw new IllegalArgumentException("No names file uploaded");
    }
    BufferedReader reader = new BufferedReader(new InputStreamReader(data, StandardCharsets.UTF_8));
    return reader.lines()
        .filter(StringUtils::isNotBlank)
        .map(n -> new CRName(code, rank, n, null));
  }

  private List<PNIssue> parse(Stream<CRName> names) {
    return names
        .map(this::parse)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toList());
  }

  private Optional<PNIssue> parse(CRName n) {
    LOG.debug("Parse: {}", n);
    Optional<ParsedNameUsage> parsed = parser.parse(n.name, n.authorship, n.rank, n.code, n);
    return parsed.map(nat -> new PNIssue(nat, n.issues));
  }

  private ParseResult parseNative(CRName n) {
    return parser.gbif().parse(n.name, n.authorship, n.rank, n.code);
  }
}
