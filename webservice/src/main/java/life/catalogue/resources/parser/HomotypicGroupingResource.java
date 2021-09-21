package life.catalogue.resources.parser;

import com.google.common.base.Function;
import com.google.common.collect.Lists;

import com.google.common.collect.Sets;

import life.catalogue.api.model.Name;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.common.tax.SciNameNormalizer;
import life.catalogue.dao.ParserConfigDao;
import life.catalogue.importer.neo.model.NeoProperties;
import life.catalogue.importer.neo.traverse.Traversals;
import life.catalogue.matching.authorship.AuthorComparator;
import life.catalogue.matching.authorship.BasionymGroup;
import life.catalogue.matching.authorship.BasionymSorter;
import life.catalogue.parser.NameParser;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSessionFactory;

import org.gbif.api.vocabulary.NameUsageIssue;

import org.gbif.nameparser.api.ParsedName;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.traversal.Evaluators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.stream.Collectors;

@Path("/parser/homotypic")
@Produces(MediaType.APPLICATION_JSON)
public class HomotypicGroupingResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(HomotypicGroupingResource.class);
  private final AuthorComparator authorComparator;
  private final BasionymSorter basSorter;

  public HomotypicGroupingResource() {
    authorComparator = new AuthorComparator(AuthorshipNormalizer.INSTANCE);
    basSorter = new BasionymSorter(authorComparator);
  }

  /**
   * Tries to homotypically group names by their terminal epithets
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public GroupingResult group(List<String> names) {
    Map<String, List<Name>> epithets = new HashMap<>();
    GroupingResult gr = new GroupingResult();
    for (String n : names) {
      if (StringUtils.isBlank(n)) continue;
      var pnOpt = NameParser.PARSER.parse(n);
      if (pnOpt.isPresent() && pnOpt.get().getName().getType().isParsable()) {
        var pn = pnOpt.get().getName();
        // ignore all supra specific names, autonyms and unparsed OTUs
        if (!pn.isBinomial()) {
          gr.ignored.add(n);
        } else {
          String epithet = SciNameNormalizer.stemEpithet(pn.getTerminalEpithet());
          epithets.computeIfAbsent(epithet, k -> new ArrayList<>()).add(pn);
        }
      } else {
        gr.ignored.add(n);
      }
    }
    // finally compare authorships for each epithet group
    for (Map.Entry<String, List<Name>> epithetGroup : epithets.entrySet()) {
      gr.groups.addAll(basSorter.groupBasionyms(epithetGroup.getValue(), a -> a));
    }
    return gr;
  }

  public static class GroupingResult {
    public final List<String> ignored = new ArrayList<>();
    public final List<BasionymGroup<Name>> groups = new ArrayList<>();
  }
  
  /**
   * Parsing names by posting plain text content using one line per scientific name.
   * Make sure to preserve new lines (\n) in the posted data, for example use --data-binary with curl:
   * <pre>
   * curl POST -H "Content-Type:text/plain" --data-binary @scientific_names.txt http://api.catalogueoflife.org/parser/homotypic
   * </pre>
   */
  @POST
  @Consumes(MediaType.TEXT_PLAIN)
  public GroupingResult groupPlainText(InputStream names) throws UnsupportedEncodingException {
    BufferedReader br = UTF8IoUtils.readerFromStream(names);
    return group(br.lines().collect(Collectors.toList()));
  }

}
