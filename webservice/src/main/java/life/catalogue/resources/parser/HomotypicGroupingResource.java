package life.catalogue.resources.parser;

import life.catalogue.api.model.HasID;
import life.catalogue.api.model.IssueContainer;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.ParsedNameUsage;
import life.catalogue.api.vocab.Issue;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.common.tax.SciNameNormalizer;
import life.catalogue.matching.authorship.AuthorComparator;
import life.catalogue.basgroup.HomotypicGroup;
import life.catalogue.basgroup.BasionymSorter;
import life.catalogue.parser.NameParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;

import org.gbif.nameparser.api.NomCode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/parser/homotypic")
@Produces(MediaType.APPLICATION_JSON)
public class HomotypicGroupingResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(HomotypicGroupingResource.class);
  private final AuthorComparator authorComparator;
  private final BasionymSorter<VerbatimName> basSorter;

  public HomotypicGroupingResource() {
    authorComparator = new AuthorComparator(AuthorshipNormalizer.INSTANCE);
    basSorter = new BasionymSorter<>(authorComparator, n -> n.index);
  }

  public static class VerbatimName implements IssueContainer, HasID<String> {
    public final int index; // ordering in original input
    public final String verbatim;
    public Name parsed;
    public final Set<Issue> issues = new HashSet<>();

    public VerbatimName(int index, String verbatim) {
      this.index = index;
      this.verbatim = verbatim;
    }

    @Override
    public Set<Issue> getIssues() {
      return issues;
    }

    @Override
    public void setIssues(Set<Issue> issues) {
      this.issues.clear();
      this.issues.addAll(issues);
    }

    @Override
    public String getId() {
      return null;
    }
  }

  public static class GroupingResult {
    public final List<String> ignored = new ArrayList<>();
    public final List<HomotypicGroup<VerbatimName>> groups = new ArrayList<>();
  }

  /**
   * Tries to homotypically group names by their terminal epithets
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public GroupingResult group(List<String> names, @QueryParam("code") NomCode code) {
    final AtomicInteger prio = new AtomicInteger(1);
    Map<String, List<VerbatimName>> epithets = new HashMap<>();
    GroupingResult gr = new GroupingResult();
    try {
      for (String n : names) {
        if (StringUtils.isBlank(n)) continue;
        var vn = new VerbatimName(prio.getAndIncrement(), n);
        Optional<ParsedNameUsage> pnOpt = NameParser.PARSER.parse(n, null, code, vn);
        if (pnOpt.isPresent() && pnOpt.get().getName().getType().isParsable()) {
          var pn = pnOpt.get().getName();
          // ignore all supra specific names, autonyms and unparsed OTUs
          if (!pn.isBinomial()) {
            gr.ignored.add(n);
          } else {
            vn.parsed = pn;
            String epithet = SciNameNormalizer.stemEpithet(pn.getTerminalEpithet());
            epithets.computeIfAbsent(epithet, k -> new ArrayList<>()).add(vn);
          }
        } else {
          gr.ignored.add(n);
        }
      }
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    // finally compare authorships for each epithet group
    for (Map.Entry<String, List<VerbatimName>> epithetGroup : epithets.entrySet()) {
      gr.groups.addAll(basSorter.groupBasionyms(code, epithetGroup.getKey(), epithetGroup.getValue(),
        a -> a.parsed,
        b -> b.first().issues.add(b.second())
      ));
    }
    return gr;
  }
  
  /**
   * Parsing names by posting plain text content using one line per scientific name.
   * Make sure to preserve new lines (\n) in the posted data, for example use --data-binary with curl:
   * <pre>
   * curl POST -H "Content-Type:text/plain" --data-binary @scientific_names.txt http://api.checklistbank.org/parser/homotypic
   * </pre>
   */
  @POST
  @Consumes(MediaType.TEXT_PLAIN)
  public GroupingResult groupPlainText(InputStream names, @QueryParam("code") NomCode code) throws IOException {
    try (BufferedReader br = UTF8IoUtils.readerFromStream(names)) {
      return group(br.lines().collect(Collectors.toList()), code);
    }
  }

  @GET
  public GroupingResult groupQueryParams(@QueryParam("name") List<String> names, @QueryParam("code") NomCode code) {
    return group(names, code);
  }

}
