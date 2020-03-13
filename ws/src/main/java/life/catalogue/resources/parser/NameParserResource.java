package life.catalogue.resources.parser;

import com.google.common.collect.Lists;
import io.dropwizard.auth.Auth;
import life.catalogue.api.model.*;
import life.catalogue.api.search.QuerySearchRequest;
import life.catalogue.dao.ParserConfigDao;
import life.catalogue.dw.auth.Roles;
import life.catalogue.parser.NameParser;
import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Path("/parser/name")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class NameParserResource {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameParserResource.class);
  private static final NameParser parser = NameParser.PARSER;
  private final ParserConfigDao dao;

  public NameParserResource(SqlSessionFactory factory) {
    dao = new ParserConfigDao(factory);
  }

  public class CRName {
    private NomCode code;
    private Rank rank;
    private String name;
  
    public CRName() {
    }
  
    public CRName(NomCode code, Rank rank, String name) {
      this.code = code;
      this.rank = rank;
      this.name = name;
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
  }
  
  /**
   * Parsing names as GET query parameters.
   */
  @GET
  public List<NameAccordingTo> parseGet(@QueryParam("code") NomCode code,
                                        @QueryParam("rank") Rank rank,
                                        @QueryParam("name") List<String> names) {
    return parse(code, rank, names.stream());
  }
  
  /**
   * Parsing names as a json array.
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public List<NameAccordingTo> parseJson(List<CRName> names) {
    return parse(names.stream());
  }
  
  /**
   * Parsing names by uploading a plain UTF-8 text file using one line per scientific name.
   * <pre>
   * curl -F names=@scientific_names.txt http://apidev.gbif.org/parser/name
   * </pre>
   */
  @POST
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public List<NameAccordingTo> parseFile(@FormDataParam("code") NomCode code,
                                         @FormDataParam("rank") Rank rank,
                                         @FormDataParam("names") InputStream file) throws UnsupportedEncodingException {
    if (file == null) {
      LOG.debug("No names file uploaded");
      return Lists.newArrayList();
    }
    BufferedReader reader = new BufferedReader(new InputStreamReader(file, Charset.forName("UTF8")));
    return parse(code, rank, reader.lines());
  }
  
  
  /**
   * Parsing names by posting plain text content using one line per scientific name.
   * Make sure to preserve new lines (\n) in the posted data, for example use --data-binary with curl:
   * <pre>
   * curl POST -H "Content-Type:text/plain" --data-binary @scientific_names.txt http://api.gbif.org/parser/name
   * </pre>
   */
  @POST
  @Consumes(MediaType.TEXT_PLAIN)
  public List<NameAccordingTo> parsePlainText(InputStream names) throws UnsupportedEncodingException {
    return parseFile(null, Rank.UNRANKED, names);
  }
  
  private List<NameAccordingTo> parse(final NomCode code, final Rank rank, Stream<String> names) {
    return parse(names.map(n -> new CRName(code, rank, n)));
  }
  
  private List<NameAccordingTo> parse(Stream<CRName> names) {
    return names
        .peek(n -> LOG.info("Parse: {}", n))
        .map(n -> parser.parse(n.name, n.rank, n.code, IssueContainer.VOID))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toList());
  }

  @GET
  @Path("config")
  public ResultPage<ParserConfig> searchConfig(@BeanParam QuerySearchRequest request, @Valid @BeanParam Page page) {
    return dao.search(request, page);
  }

  @POST
  @RolesAllowed({Roles.ADMIN})
  @Path("config")
  public String createConfig(@Valid ParserConfig obj, @Auth ColUser user) {
    dao.putName(obj, user.getKey());
    return obj.getId();
  }

  @GET
  @Path("config/{id}")
  public ParserConfig getConfig(@PathParam("id") String id) {
    return dao.get(id);
  }

  @DELETE
  @Path("config/{id}")
  @RolesAllowed({Roles.ADMIN})
  public void deleteConfig(@PathParam("id") String id, @Auth ColUser user) {
    dao.deleteName(id, user.getKey());
  }

}
