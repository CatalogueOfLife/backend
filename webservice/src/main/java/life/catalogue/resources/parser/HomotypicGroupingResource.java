package life.catalogue.resources.parser;

import life.catalogue.api.model.Name;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.common.tax.SciNameNormalizer;
import life.catalogue.matching.authorship.AuthorComparator;
import life.catalogue.matching.authorship.BasionymGroup;
import life.catalogue.matching.authorship.BasionymSorter;
import life.catalogue.parser.NameParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

  public static class VerbatimName {
    public final String verbatim;
    public final Name parsed;

    public VerbatimName(String verbatim, Name parsed) {
      this.verbatim = verbatim;
      this.parsed = parsed;
    }
  }

  public static class GroupingResult {
    public final List<String> ignored = new ArrayList<>();
    public final List<BasionymGroup<VerbatimName>> groups = new ArrayList<>();
  }

  /**
   * Tries to homotypically group names by their terminal epithets
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public GroupingResult group(List<String> names) {
    Map<String, List<VerbatimName>> epithets = new HashMap<>();
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
          pn.setRemarks(n);
          String epithet = SciNameNormalizer.stemEpithet(pn.getTerminalEpithet());
          epithets.computeIfAbsent(epithet, k -> new ArrayList<>()).add(new VerbatimName(n, pn));
        }
      } else {
        gr.ignored.add(n);
      }
    }
    // finally compare authorships for each epithet group
    for (Map.Entry<String, List<VerbatimName>> epithetGroup : epithets.entrySet()) {
      gr.groups.addAll(basSorter.groupBasionyms(epithetGroup.getValue(), a -> a.parsed));
    }
    return gr;
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
  public GroupingResult groupPlainText(InputStream names) throws IOException {
    try (BufferedReader br = UTF8IoUtils.readerFromStream(names)) {
      return group(br.lines().collect(Collectors.toList()));
    }
  }

}
