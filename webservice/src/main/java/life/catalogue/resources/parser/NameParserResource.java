package life.catalogue.resources.parser;

import life.catalogue.api.model.*;
import life.catalogue.api.search.QuerySearchRequest;
import life.catalogue.api.vocab.Issue;
import life.catalogue.dao.ParserConfigDao;
import life.catalogue.dw.auth.Roles;
import life.catalogue.parser.NameParser;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import org.apache.ibatis.session.SqlSessionFactory;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dropwizard.auth.Auth;

@Path("/parser/name")
@Produces(MediaType.APPLICATION_JSON)
public class NameParserResource {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameParserResource.class);
  private static final NameParser parser = NameParser.PARSER;
  private final ParserConfigDao dao;

  public NameParserResource(SqlSessionFactory factory) {
    dao = new ParserConfigDao(factory);
  }

  public class CRName implements IssueContainer {
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
    public void addIssue(Issue issue) {
      issues.add(issue);
    }

    @Override
    public boolean removeIssue(Issue issue) {
      return issues.remove(issue);
    }

    @Override
    public boolean hasIssue(Issue issue) {
      return issues.contains(issue);
    }
  }

  /**
   * For name parsing results only.
   * We flatten results here to just a single json object without a usage with a nested name.
   * And add issues.
   */
  static class PNIssue extends Name {
    private boolean extinct;
    private String taxonomicNote;
    private String publishedIn;
    private Set<Issue> issues;

    public PNIssue(ParsedNameUsage pnu, Set<Issue> issues) {
      super(pnu.getName());
      this.extinct = pnu.isExtinct();
      this.taxonomicNote = pnu.getTaxonomicNote();
      this.publishedIn = pnu.getPublishedIn();
      this.issues = issues;
    }

    public Set<Issue> getIssues() {
      return issues;
    }
  }


  /**
   * Parsing names as GET query parameters.
   */
  @GET
  public Optional<PNIssue> parseGet(@QueryParam("code") NomCode code,
                                    @QueryParam("rank") Rank rank,
                                    @QueryParam("name") String name,
                                    @QueryParam("authorship") String authorship) {
    return parse(new CRName(code, rank, name, authorship));
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
   * curl -F names=@scientific_names.txt http://api.catalogueoflife.org/parser/name
   * </pre>
   */
  @POST
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public List<PNIssue> parseFile(@FormDataParam("code") NomCode code,
                                 @FormDataParam("rank") Rank rank,
                                 @FormDataParam("names") InputStream file) throws UnsupportedEncodingException {
    if (file == null) {
      throw new IllegalArgumentException("No names file uploaded");
    }
    BufferedReader reader = new BufferedReader(new InputStreamReader(file, StandardCharsets.UTF_8));
    return parse(code, rank, reader.lines());
  }
  
  
  /**
   * Parsing names by posting plain text content using one line per scientific name.
   * Make sure to preserve new lines (\n) in the posted data, for example use --data-binary with curl:
   * <pre>
   * curl POST -H "Content-Type:text/plain" --data-binary @scientific_names.txt http://api.catalogueoflife.org/parser/name
   * </pre>
   */
  @POST
  @Consumes(MediaType.TEXT_PLAIN)
  public List<PNIssue> parsePlainText(InputStream names) throws UnsupportedEncodingException {
    return parseFile(null, Rank.UNRANKED, names);
  }
  
  private List<PNIssue> parse(final NomCode code, final Rank rank, Stream<String> names) {
    return parse(names.map(n -> new CRName(code, rank, n, null)));
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

  @GET
  @Path("config")
  public ResultPage<ParserConfig> searchConfig(@BeanParam QuerySearchRequest request, @Valid @BeanParam Page page) {
    return dao.search(request, page);
  }

  @POST
  @RolesAllowed({Roles.ADMIN})
  @Path("config")
  public String createConfig(@Valid ParserConfig config, @Auth User user) {
    dao.putName(config, user.getKey());
    return config.getId();
  }

  @POST
  @RolesAllowed({Roles.ADMIN})
  @Path("config/batch")
  public List<String> createConfigs(@Valid List<ParserConfig> configs, @Auth User user) {
    List<String> ids = new ArrayList<>(configs.size());
    for (ParserConfig pc : configs) {
      if (pc == null) continue;
      dao.putName(pc, user.getKey());
      ids.add(pc.getId());
    }
    return ids;
  }

  @GET
  @Path("config/{id}")
  public ParserConfig getConfig(@PathParam("id") String id) {
    return dao.get(id);
  }

  @DELETE
  @Path("config/{id}")
  @RolesAllowed({Roles.ADMIN})
  public void deleteConfig(@PathParam("id") String id, @Auth User user) {
    dao.deleteName(id, user.getKey());
  }

}
