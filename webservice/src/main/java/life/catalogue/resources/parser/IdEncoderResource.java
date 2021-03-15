package life.catalogue.resources.parser;

import com.google.common.collect.Lists;
import life.catalogue.common.id.IdConverter;
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
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Path("/parser/idconverter")
@Produces(MediaType.APPLICATION_JSON)
public class IdEncoderResource {

  @GET
  @Path("encode")
  public String encode(@QueryParam("id") int id, @QueryParam("format") String format) {
    return findConverter(format).encode(id);
  }

  @GET
  @Path("decode")
  public Integer decode(@QueryParam("id") String id, @QueryParam("format") String format) {
    return findConverter(format).decode(id);
  }

  static IdConverter findConverter(String name){
    if (name != null) {
      switch (name.toUpperCase().trim()) {
        case "LATIN36":
          return IdConverter.LATIN36;
        case "LATIN32":
          return IdConverter.LATIN32;
        case "HEX":
          return IdConverter.HEX;
        case "BASE64":
          return IdConverter.BASE64;
      }
    }
    return IdConverter.LATIN29;
  }
}
