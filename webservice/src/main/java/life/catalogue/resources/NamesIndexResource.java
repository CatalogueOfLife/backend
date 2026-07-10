package life.catalogue.resources;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import life.catalogue.WsServerConfig;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.common.util.RegexUtils;
import life.catalogue.db.mapper.NameMatchMapper;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.NamesIndexMapper;
import life.catalogue.dw.auth.Roles;
import life.catalogue.interpreter.NameInterpreter;
import life.catalogue.matching.TaxGroupAnalyzer;
import life.catalogue.matching.nidx.NameIndex;
import life.catalogue.matching.nidx.NameIndexStore;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

@Path("/nidx")
@Produces(MediaType.APPLICATION_JSON)
public class NamesIndexResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NamesIndexResource.class);
  private final NameIndex ni;
  private final NameInterpreter interpreter = new NameInterpreter(new DatasetSettings(), true);
  private final SqlSessionFactory factory;
  private final WsServerConfig cfg;
  private final TaxGroupAnalyzer analyzer;

  public NamesIndexResource(NameIndex ni, SqlSessionFactory factory, WsServerConfig cfg) {
    this.ni = ni;
    this.factory = factory;
    this.cfg = cfg;
    this.analyzer = new TaxGroupAnalyzer();
  }

  @GET
  @Path("metadata")
  public NidxMetadata getCreated() {
    return new NidxMetadata(ni.store());
  }

  public static class NidxMetadata {
    public final String type;
    public final LocalDateTime created;
    public final int size;

    public NidxMetadata(NameIndexStore store) {
      this.created = store.created();
      this.size = store.count();
      this.type = store.getClass().getSimpleName();
    }
  }

  @GET
  @Path("{key}")
  public NidxWithLabels get(@PathParam("key") int key, @Context SqlSession session) {
    var n = ni.get(key);
    if (n == null) throw NotFoundException.notFound(NameIndexEntry.class, key);
    var labels = session.getMapper(NameMatchMapper.class).labelCounts(key);
    return new NidxWithLabels(key, n.getScientificName(), labels);
  }

  public static class NidxWithLabels {
    public final int nidx;
    public final String scientificName;
    public final List<LabelCount> labels;

    public NidxWithLabels(int nidx, String scientificName, List<LabelCount> labels) {
      this.nidx = nidx;
      this.scientificName = scientificName;
      this.labels = labels;
    }
  }

  @GET
  @Path("{key}/usages")
  public ResultPage<SimpleNameInDatasetClassified> relatedUsages(@PathParam("key") int key, @Valid @BeanParam Page page) {
    Page p = page == null ? new Page() : page;
    try (SqlSession session = factory.openSession(true)) {
      var num = session.getMapper(NameUsageMapper.class);
      var res = num.listByNamesIndexIDGlobalClassified(key, p);
      res.forEach(sn -> {
        sn.setGroup(analyzer.analyze(sn, sn.getClassification()));
      });
      return new ResultPage<>(p, res, () -> num.countByNamesIndexIDGlobalClassified(key));
    }
  }

  @GET
  @Hidden
  @Deprecated
  @Path("match")
  public NameMatch match(@QueryParam("q") String q,
                         @QueryParam("name") String name,
                         @QueryParam("scientificName") String sciname,
                         @QueryParam("authorship") String authorship,
                         @QueryParam("rank") Rank rank,
                         @QueryParam("code") NomCode code,
                         @QueryParam("verbose") boolean verbose) throws InterruptedException {
    SimpleName sn = new SimpleName(null, ObjectUtils.coalesce(sciname, name, q), authorship, rank, code);
    Name n = interpreter.interpret(sn, IssueContainer.VOID)
                        .orElseThrow(() -> new IllegalArgumentException("Failed to interpret name")).getName();
    return ni.match(n, false, verbose);
  }

  @GET
  @Path("pattern")
  public List<NameIndexEntry> searchByRegex(@QueryParam("q") String regex,
                                       @QueryParam("canonical") @DefaultValue("true") boolean canonical,
                                       @QueryParam("rank") Rank rank,
                                       @Valid @BeanParam Page page,
                                       @Context SqlSession session) {
    RegexUtils.validatePattern(regex);
    Page p = page == null ? new Page() : page;
    return session.getMapper(NamesIndexMapper.class).listByRegex(regex, canonical, rank, p);
  }

  @POST
  @Hidden
  @Deprecated
  @Path("compact")
  @RolesAllowed({Roles.ADMIN})
  public void compact() {
    ni.store().compact();
  }

}
