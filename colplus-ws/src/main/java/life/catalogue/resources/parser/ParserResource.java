package life.catalogue.resources.parser;

import com.google.common.collect.Lists;
import life.catalogue.parser.*;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Path("/parser")
@Produces(MediaType.APPLICATION_JSON)
public class ParserResource<T> {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(ParserResource.class);
  private final Map<String, Parser<?>> parsers = new HashMap<>();

  public ParserResource() {
    parsers.put("boolean", BooleanParser.PARSER);
    parsers.put("country", CountryParser.PARSER);
    parsers.put("datasettype", DatasetTypeParser.PARSER);
    parsers.put("date", DateParser.PARSER);
    parsers.put("distributionstatus", DistributionStatusParser.PARSER);
    parsers.put("gazetteer", GazetteerParser.PARSER);
    parsers.put("geotime", GeoTimeParser.PARSER);
    parsers.put("integer", IntegerParser.PARSER);
    parsers.put("language", LanguageParser.PARSER);
    parsers.put("license", LicenseParser.PARSER);
    parsers.put("lifezone", LifezoneParser.PARSER);
    parsers.put("mediatype", MediaTypeParser.PARSER);
    parsers.put("nomcode", NomCodeParser.PARSER);
    parsers.put("nomreltype", NomRelTypeParser.PARSER);
    parsers.put("nomstatus", NomStatusParser.PARSER);
    parsers.put("rank", RankParser.PARSER);
    parsers.put("referencetype", ReferenceTypeParser.PARSER);
    parsers.put("sex", SexParser.PARSER);
    parsers.put("taxonomicstatus", TaxonomicStatusParser.PARSER);
    parsers.put("textformat", TextFormatParser.PARSER);
    parsers.put("typestatus", TypeStatusParser.PARSER);
    parsers.put("uri", UriParser.PARSER);
  }

  public static class ParseResult<T> {
    public final String original;
    public final T parsed;
    public final boolean parsable;

    public ParseResult(String original, SafeParser<T> parsed) {
      this.original = original;
      this.parsed = parsed.orNull();
      this.parsable = parsed.isParsable();
    }
  }
  
  /**
   * Parsing names as GET "q" query parameters.
   */
  @GET
  @Path("{type}")
  public List<ParseResult<?>> parseGet(@PathParam("type") String type, @QueryParam("q") List<String> values) {
    return parse(type, values.stream());
  }
  
  /**
   * Parsing values as a json array.
   */
  @POST
  @Path("{type}")
  @Consumes(MediaType.APPLICATION_JSON)
  public List<ParseResult<?>> parseJson(@PathParam("type") String type, List<String> data) {
    return parse(type, data.stream());
  }
  
  /**
   * Parsing values by uploading a plain UTF-8 text file as "q" using one line per value.
   * <pre>
   * curl -F q=@scientific_names.txt http://apidev.gbif.org/parser/name
   * </pre>
   */
  @POST
  @Path("{type}")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public List<ParseResult<?>> parseFile(@PathParam("type") String type, @FormDataParam("q") InputStream file) throws UnsupportedEncodingException {
    if (file == null) {
      LOG.debug("No file uploaded");
      return Lists.newArrayList();
    }
    return parseInputStream(type, file);
  }
  
  
  /**
   * Parsing names by posting plain text content using one line per value.
   * Make sure to preserve new lines (\n) in the posted data, for example use --data-binary with curl:
   * <pre>
   * curl POST -H "Content-Type:text/plain" --data-binary @scientific_names.txt http://api.gbif.org/parser/name
   * </pre>
   */
  @POST
  @Path("{type}")
  @Consumes(MediaType.TEXT_PLAIN)
  public List<ParseResult<?>> parsePlainText(@PathParam("type") String type, InputStream names) throws UnsupportedEncodingException {
    return parseInputStream(type, names);
  }


  private List<ParseResult<?>> parseInputStream(String type, InputStream data) throws UnsupportedEncodingException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(data, StandardCharsets.UTF_8));
    return parse(type, reader.lines());
  }

  private List<ParseResult<?>> parse(String type, Stream<String> rows) {
    if (!parsers.containsKey(type.toLowerCase())) {
      throw new NotFoundException("No parser for "+type+" exists");
    }

    final Parser<?> parser = parsers.get(type);
    return rows
        .map(n -> new ParseResult<>(n, SafeParser.parse(parser, n)))
        .collect(Collectors.toList());
  }
  
}
