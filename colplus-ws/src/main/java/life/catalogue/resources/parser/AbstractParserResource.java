package life.catalogue.resources.parser;

import com.google.common.collect.Lists;
import life.catalogue.parser.Parser;
import life.catalogue.parser.SafeParser;
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
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Produces(MediaType.APPLICATION_JSON)
abstract class AbstractParserResource<T> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(AbstractParserResource.class);
  private final Parser<T> parser;

  protected AbstractParserResource(Parser<T> parser) {
    this.parser = parser;
  }

  public static class ParseResult<T> {
    private final String original;
    private final T parsed;
    private final boolean parsable;

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
  public List<ParseResult<T>> parseGet(@QueryParam("q") List<String> values) {
    return parse(values.stream());
  }
  
  /**
   * Parsing values as a json array.
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public List<ParseResult<T>> parseJson(List<String> data) {
    return parse(data.stream());
  }
  
  /**
   * Parsing values by uploading a plain UTF-8 text file as "q" using one line per value.
   * <pre>
   * curl -F q=@scientific_names.txt http://apidev.gbif.org/parser/name
   * </pre>
   */
  @POST
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public List<ParseResult<T>> parseFile(@FormDataParam("q") InputStream file) throws UnsupportedEncodingException {
    if (file == null) {
      LOG.debug("No file uploaded");
      return Lists.newArrayList();
    }
    return parseInputStream(file);
  }
  
  
  /**
   * Parsing names by posting plain text content using one line per value.
   * Make sure to preserve new lines (\n) in the posted data, for example use --data-binary with curl:
   * <pre>
   * curl POST -H "Content-Type:text/plain" --data-binary @scientific_names.txt http://api.gbif.org/parser/name
   * </pre>
   */
  @POST
  @Consumes(MediaType.TEXT_PLAIN)
  public List<ParseResult<T>> parsePlainText(InputStream names) throws UnsupportedEncodingException {
    return parseInputStream(names);
  }

  private List<ParseResult<T>> parseInputStream(InputStream data) throws UnsupportedEncodingException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(data, StandardCharsets.UTF_8));
    return parse(reader.lines());
  }

  private List<ParseResult<T>> parse(Stream<String> rows) {
    return rows
        .map(n -> new ParseResult<>(n, SafeParser.parse(parser, n)))
        .collect(Collectors.toList());
  }
  
}
