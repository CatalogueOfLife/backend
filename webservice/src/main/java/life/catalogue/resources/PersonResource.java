package life.catalogue.resources;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.AuthorshipPersonMatch;
import life.catalogue.api.model.Person;
import life.catalogue.api.model.PersonInfo;
import life.catalogue.api.model.PersonMatch;
import life.catalogue.api.util.VocabularyUtils;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.common.ws.MoreMediaTypes;
import life.catalogue.matching.person.PersonFiles;
import life.catalogue.matching.person.PersonMatchService;
import life.catalogue.matching.person.PersonResolver;
import life.catalogue.matching.person.PersonStore;
import life.catalogue.matching.person.PersonTables;
import life.catalogue.parser.NomCodeParser;
import life.catalogue.parser.UnparsableException;

import org.gbif.nameparser.api.NomCode;

import java.sql.SQLException;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSessionFactory;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

/**
 * The person registry: persons by any id they answer to, and author citations or whole authorships matched to them.
 */
@Path("/person")
@Produces(MediaType.APPLICATION_JSON)
public class PersonResource {
  private final PersonStore store;
  private final SqlSessionFactory factory;
  private final PersonMatchService matcher;

  public PersonResource(PersonStore store, SqlSessionFactory factory) {
    this.store = store;
    this.factory = factory;
    this.matcher = new PersonMatchService(store, PersonResolver.Margins.DEFAULT);
  }

  /**
   * One author citation, narrowed by the code, year and group of the name it authored.
   */
  @GET
  @Path("match")
  public PersonMatch match(@QueryParam("q") String q, @QueryParam("code") String code, @QueryParam("year") String year,
                           @QueryParam("group") String group) {
    return matcher.match(required(q), code(code), year(year), group(group));
  }

  /**
   * A whole authorship, every author of every slot matched.
   */
  @GET
  @Path("match/authorship")
  public AuthorshipPersonMatch matchAuthorship(@QueryParam("q") String q, @QueryParam("code") String code,
                                               @QueryParam("group") String group) {
    return matcher.matchAuthorship(required(q), code(code), group(group));
  }

  /**
   * The registry as a zip of its three files, as the admin import takes it.
   */
  @GET
  @Path("export")
  @Produces(MoreMediaTypes.APP_ZIP)
  public Response export() throws SQLException {
    // read before anything is streamed, so a failing database is an error response and not a broken download
    PersonFiles.Content c = PersonTables.read(factory);
    StreamingOutput stream = os -> PersonFiles.writeZip(os, c);
    return Response.ok(stream).header("Content-Disposition", "attachment; filename=\"persons.zip\"").build();
  }

  /**
   * @param id any id the person answers to: its own, a former one or a prefixed authority id such as ipni:12653-1
   */
  @GET
  @Path("{id}")
  public PersonInfo get(@PathParam("id") String id) {
    PersonInfo info = store.info(id);
    if (info == null) {
      throw NotFoundException.notFound(Person.class, id);
    }
    return info;
  }

  private static String required(@Nullable String q) {
    if (StringUtils.isBlank(q)) {
      throw new IllegalArgumentException("Parameter q is required");
    }
    return q;
  }

  // parameters are parsed here rather than by the param converters, so the resource methods can be called and tested
  // directly; an IllegalArgumentException is a 400, as QueryParam400Mapper makes a failed conversion
  @Nullable
  static NomCode code(@Nullable String code) {
    if (StringUtils.isBlank(code)) return null;
    try {
      return NomCodeParser.PARSER.parse(code).orElseThrow(() -> new IllegalArgumentException("Unknown code " + code));
    } catch (UnparsableException e) {
      throw new IllegalArgumentException("Unknown code " + code);
    }
  }

  @Nullable
  static TaxGroup group(@Nullable String group) {
    if (StringUtils.isBlank(group)) return null;
    return VocabularyUtils.lookup(group, TaxGroup.class).orElseThrow(() -> new IllegalArgumentException("Unknown group " + group));
  }

  @Nullable
  static Integer year(@Nullable String year) {
    if (StringUtils.isBlank(year)) return null;
    try {
      return Integer.valueOf(year.trim());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("The year " + year + " is no number");
    }
  }
}
