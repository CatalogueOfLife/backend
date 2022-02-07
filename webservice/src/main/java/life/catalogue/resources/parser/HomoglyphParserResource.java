package life.catalogue.resources.parser;

import com.fasterxml.jackson.annotation.JsonProperty;

import life.catalogue.api.model.*;
import life.catalogue.api.search.QuerySearchRequest;
import life.catalogue.api.vocab.Issue;
import life.catalogue.common.text.UnicodeUtils;
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

@Path("/parser/homoglyph")
@Produces(MediaType.APPLICATION_JSON)
public class HomoglyphParserResource {

  public class HomoglyphResult {
    public String original;
    public String canonical;
    public List<Integer> positions;

    @JsonProperty("hasHomoglyphs")
    public boolean hasHomoglyphs() {
      return !original.equals(canonical);
    }
  }


  @GET
  public HomoglyphResult parse(@QueryParam("q") String input) {
    if (input == null) {
      throw new IllegalArgumentException("Input string required");
    }
    HomoglyphResult result = new HomoglyphResult();
    result.original = input;
    result.canonical = UnicodeUtils.replaceHomoglyphs(input);
    if (result.hasHomoglyphs()) {
      result.positions = new ArrayList<>();
      int pos = 0;
      var iter = input.codePoints().iterator();
      var iter2 = result.canonical.codePoints().iterator();
      while(iter.hasNext()) {
        pos++;
        int cp = iter.nextInt();
        int cp2 = iter2.nextInt();
        if (cp != cp2) {
          result.positions.add(pos);
        }
      }
    }
    return result;
  }
  
  @POST
  @Consumes({MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON})
  public HomoglyphResult parseJson(String input) {
    return parse(input);
  }

}
