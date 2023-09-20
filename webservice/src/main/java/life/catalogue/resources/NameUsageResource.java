package life.catalogue.resources;

import com.google.common.base.Preconditions;

import io.dropwizard.auth.Auth;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.api.search.*;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.NomRelType;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.cache.LatestDatasetKeyCache;
import life.catalogue.common.util.RegexUtils;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.dao.SynonymDao;
import life.catalogue.dao.TaxonDao;
import life.catalogue.dao.TxtTreeDao;
import life.catalogue.db.mapper.*;
import life.catalogue.db.tree.PrinterFactory;
import life.catalogue.db.tree.TextTreePrinter;
import life.catalogue.dw.auth.Roles;
import life.catalogue.es.InvalidQueryException;
import life.catalogue.es.NameUsageSearchService;
import life.catalogue.es.NameUsageSuggestionService;

import life.catalogue.importer.neo.model.NeoRel;
import life.catalogue.importer.neo.model.NeoUsage;
import life.catalogue.importer.neo.model.RelType;
import life.catalogue.importer.txttree.TxtTreeInserter;

import org.apache.commons.lang3.StringUtils;

import org.apache.ibatis.session.SqlSessionFactory;

import org.gbif.nameparser.api.Rank;

import java.io.*;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import javax.annotation.security.RolesAllowed;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.*;

import org.apache.ibatis.session.SqlSession;

import org.gbif.txtree.SimpleTreeNode;
import org.gbif.txtree.Tree;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.annotation.Timed;

@Produces(MediaType.APPLICATION_JSON)
@Path("/dataset/{key}/nameusage")
public class NameUsageResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameUsageResource.class);
  private final NameUsageSearchService searchService;
  private final NameUsageSuggestionService suggestService;
  private final LatestDatasetKeyCache datasetKeyCache;

  public NameUsageResource(NameUsageSearchService search, NameUsageSuggestionService suggest, LatestDatasetKeyCache datasetKeyCache) {
    this.searchService = search;
    this.suggestService = suggest;
    this.datasetKeyCache = datasetKeyCache;
  }

  @GET
  public ResultPage<NameUsageBase> list(@PathParam("key") int datasetKey,
                                        @QueryParam("q") String q,
                                        @QueryParam("rank") Rank rank,
                                        @QueryParam("nidx") Integer namesIndexID,
                                        @Valid @BeanParam Page page,
                                        @Context SqlSession session) {
    Page p = page == null ? new Page() : page;
    NameUsageMapper mapper = session.getMapper(NameUsageMapper.class);
    List<NameUsageBase> result;
    Supplier<Integer> count;
    if (namesIndexID != null) {
      result = mapper.listByNamesIndexOrCanonicalID(datasetKey, namesIndexID, p);
      count = () -> mapper.countByNamesIndexID(namesIndexID, datasetKey);
    } else if (q != null) {
      result = mapper.listByName(datasetKey, q, rank, p);
      count = () -> result.size();
    } else {
      result = mapper.list(datasetKey, p);
      count = () -> mapper.count(datasetKey);
    }
    return new ResultPage<>(p, result, count);
  }

  @GET
  @Path("{id}")
  public NameUsageBase get(@PathParam("key") int datasetKey, @PathParam("id") String id, @Context SqlSession session) {
    var key = DSID.of(datasetKey, id);
    var num = session.getMapper(NameUsageMapper.class);
    NameUsageBase u = num.get(key);
    if (u == null) {
      var info = DatasetInfoCache.CACHE.info(datasetKey);
      if (info.origin == DatasetOrigin.PROJECT) {
        // try latest release first
        var latest = datasetKeyCache.getLatestRelease(datasetKey, false);
        if (latest != null) {
          u = num.get(DSID.of(latest, id));
        }
        if (u == null) {
          // try last archived usage with project key as last resort before we send a 404
          u = session.getMapper(ArchivedNameUsageMapper.class).get(key);
        }
      }
    }
    return u;
  }

  @GET
  @Path("{id}/related")
  public List<NameUsageBase> related(@PathParam("key") int datasetKey,
                                     @PathParam("id") String id,
                                     @QueryParam("datasetKey") List<Integer> datasetKeys,
                                     @QueryParam("publisherKey") UUID publisherKey,
                                     @Context SqlSession session) {
    NameUsageMapper num = session.getMapper(NameUsageMapper.class);
    var key = DSID.of(datasetKey, id);
    num.existsOrThrow(key);
    return num.listRelated(key, datasetKeys, publisherKey);
  }

  @GET
  @Path("{id}/source")
  public VerbatimSource source(@PathParam("key") int datasetKey, @PathParam("id") String id, @Context SqlSession session) {
    return session.getMapper(VerbatimSourceMapper.class).getWithSources(DSID.of(datasetKey, id));
  }

  @GET
  @Timed
  @Path("pattern")
  public List<SimpleNameWithDecision> searchDatasetByRegex(@PathParam("key") int datasetKey,
                                               @QueryParam("projectKey") Integer projectKey,
                                               @QueryParam("regex") String regex,
                                               @QueryParam("status") TaxonomicStatus status,
                                               @QueryParam("rank") Rank rank,
                                               @QueryParam("decisionMode") String decisionMode,
                                               @Valid @BeanParam Page page,
                                               @Context SqlSession session) {
    RegexUtils.validatePattern(regex);
    Page p = page == null ? new Page() : page;
    Boolean withDecision = null; // true if decision need to be present, null indifferent
    EditorialDecision.Mode mode = null; // mode of decision to filter by
    if (NameUsageRequest.IS_NULL.equalsIgnoreCase(decisionMode)) {
      withDecision = false;
    } else if (NameUsageRequest.IS_NOT_NULL.equalsIgnoreCase(decisionMode)) {
      withDecision = true;
    } else if (!StringUtils.isBlank(decisionMode)) {
      withDecision = true;
      mode = EditorialDecision.Mode.valueOf(decisionMode.toUpperCase().trim());
    }
    if (withDecision != null && projectKey == null) {
      throw new IllegalArgumentException("projectKey required when decisionMode is present");
    }
    return session.getMapper(NameUsageMapper.class).listByRegex(datasetKey, projectKey, regex, status, rank, withDecision, mode, p);
  }

  @GET
  @Path("search")
  public ResultPage<NameUsageWrapper> searchDataset(@PathParam("key") int datasetKey,
                                                    @BeanParam NameUsageSearchRequest query,
                                                    @Valid @BeanParam Page page,
                                                    @Context UriInfo uri) throws InvalidQueryException {
    checkIllegalDatasetKeyParam(datasetKey, query, uri);
    return searchService.search(query, page);
  }

  @POST
  @Path("search")
  public ResultPage<NameUsageWrapper> searchPOST(@PathParam("key") int datasetKey,
                                                 @Valid NameUsageSearchResource.SearchRequestBody req,
                                                 @Context UriInfo uri) throws InvalidQueryException {
    return searchDataset(datasetKey, req.request, req.page, uri);
  }

  @GET
  @Path("suggest")
  public NameUsageSuggestResponse suggestDataset(@PathParam("key") int datasetKey,
                                                 @BeanParam NameUsageSuggestRequest query,
                                                 @Context UriInfo uri) throws InvalidQueryException {
    checkIllegalDatasetKeyParam(datasetKey, query, uri);
    return suggestService.suggest(query);
  }

  NameUsageRequest checkIllegalDatasetKeyParam(int datasetKey, NameUsageRequest query, UriInfo uri){
    query.addFilters(uri.getQueryParameters());
    if (query.hasFilter(NameUsageSearchParameter.DATASET_KEY) && !query.getFilterValue(NameUsageSearchParameter.DATASET_KEY).equals(datasetKey)) {
      throw new IllegalArgumentException("No further datasetKey parameter allowed, suggest already scoped to datasetKey=" + datasetKey);
    }
    query.setDatasetFilter(datasetKey);
    return query;
  }

}
