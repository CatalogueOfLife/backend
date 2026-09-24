package life.catalogue.resources;

import life.catalogue.api.event.PersonsChanged;
import life.catalogue.api.model.JobInfo;
import life.catalogue.api.model.User;
import life.catalogue.common.ws.MoreMediaTypes;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.config.PersonConfig;
import life.catalogue.dao.JobDao;
import life.catalogue.dw.auth.Roles;
import life.catalogue.event.EventBroker;
import life.catalogue.matching.person.PersonFiles;
import life.catalogue.matching.person.PersonTables;
import life.catalogue.matching.person.harvest.PersonHarvestJob;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;

import org.apache.ibatis.session.SqlSessionFactory;

import io.dropwizard.auth.Auth;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

/**
 * Writes the person registry: a harvest from its authorities, or an import of its files.
 */
@Path("/admin/persons")
@Hidden
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({Roles.ADMIN})
public class PersonAdminResource {
  private final SqlSessionFactory factory;
  private final JobExecutor exec;
  private final EventBroker broker;
  private final PersonConfig cfg;

  public record Counts(int persons, int names, int relations) {
  }

  public PersonAdminResource(SqlSessionFactory factory, JobExecutor exec, EventBroker broker, PersonConfig cfg) {
    this.factory = factory;
    this.exec = exec;
    this.broker = broker;
    this.cfg = cfg;
  }

  @POST
  @Path("harvest")
  public JobInfo harvest(@Auth User user) {
    var job = PersonHarvestJob.live(user.getKey(), factory, broker, cfg);
    exec.submit(job);
    return JobDao.buildInfo(job);
  }

  /**
   * Replaces the registry by a zip of its three files as /person/export writes them. An inconsistent registry is a 400
   * and writes nothing.
   */
  @POST
  @Path("import")
  @Consumes({MoreMediaTypes.APP_ZIP, MoreMediaTypes.APP_ZIP_ALT1, MoreMediaTypes.APP_ZIP_ALT2, MoreMediaTypes.APP_ZIP_ALT3})
  public Counts importZip(InputStream zip, @Auth User user) throws IOException, SQLException {
    PersonFiles.Content c = PersonFiles.readZip(zip);
    PersonTables.replace(factory, c);
    broker.publish(new PersonsChanged(user.getKey()));
    return new Counts(c.persons().size(), c.names().size(), c.relations().size());
  }
}
