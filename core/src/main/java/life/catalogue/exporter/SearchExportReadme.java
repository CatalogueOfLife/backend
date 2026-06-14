package life.catalogue.exporter;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.search.NameUsageRequest;
import life.catalogue.api.search.NameUsageSearchParameter;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.common.io.UTF8IoUtils;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.ws.rs.core.UriBuilder;

/**
 * Renders the human readable README.md that accompanies a {@link SearchExport} archive.
 * It describes the dataset (citation, version), the executed search and a repeatable link back to the
 * same search in ChecklistBank.
 */
class SearchExportReadme {

  static void write(File f, Dataset dataset, NameUsageSearchRequest search, URI clbURI, int usageCount, LocalDateTime generated) throws IOException {
    try (Writer w = UTF8IoUtils.writerFromFile(f)) {
      w.write("# ChecklistBank search download\n\n");
      w.write("This archive holds the result of a name usage search exported from ChecklistBank as a ColDP `NameUsage.tsv`.\n");
      w.write(String.format("It contains **%,d name usages** and was generated on %s.\n\n",
        usageCount, generated == null ? "" : generated.format(DateTimeFormatter.ISO_LOCAL_DATE)));

      // dataset
      w.write("## Dataset\n\n");
      w.write(String.format("- **Title:** %s\n", nullToDash(dataset.getTitle())));
      if (dataset.getAlias() != null) {
        w.write(String.format("- **Alias:** %s\n", dataset.getAlias()));
      }
      w.write(String.format("- **Key:** %s\n", dataset.getKey()));
      if (dataset.getVersion() != null) {
        w.write(String.format("- **Version:** %s\n", dataset.getVersion()));
      }
      if (dataset.getIssued() != null) {
        w.write(String.format("- **Issued:** %s\n", dataset.getIssued()));
      }
      if (dataset.getDoi() != null) {
        w.write(String.format("- **DOI:** https://doi.org/%s\n", dataset.getDoi().getDoiName()));
      }
      w.write("\n");
      String citation = dataset.getCitationText();
      if (citation != null) {
        w.write("### Citation\n\n");
        w.write("Please cite the source dataset as:\n\n");
        w.write("> " + citation.replace("\n", " ").trim() + "\n\n");
      }

      // search
      w.write("## Search\n\n");
      w.write("The export reflects the following name usage search:\n\n");
      writeSearchDescription(w, search);
      w.write("\n");

      // repeatable link
      URI link = searchUrl(clbURI, dataset.getKey(), search);
      if (link != null) {
        w.write("## Repeat this search\n\n");
        w.write("Open the same search in ChecklistBank:\n\n");
        w.write("<" + link + ">\n");
      }
    }
  }

  private static void writeSearchDescription(Writer w, NameUsageSearchRequest search) throws IOException {
    boolean any = false;
    if (search.hasQ()) {
      w.write(String.format("- **q** (full text): `%s`\n", search.getQ()));
      any = true;
    }
    if (search.getFilters() != null) {
      for (Map.Entry<NameUsageSearchParameter, Set<Object>> e : search.getFilters().entrySet()) {
        // the dataset filter is implied by the dataset section above
        if (e.getKey() == NameUsageSearchParameter.DATASET_KEY) {
          continue;
        }
        String values = e.getValue().stream().map(SearchExportReadme::valueToString).collect(Collectors.joining(", "));
        w.write(String.format("- **%s:** %s\n", e.getKey().name(), values));
        any = true;
      }
    }
    if (search.getSortBy() != null) {
      w.write(String.format("- **sorted by:** %s%s\n", search.getSortBy().name().toLowerCase(), search.isReverse() ? " (reversed)" : ""));
      any = true;
    }
    if (search.getMinRank() != null) {
      w.write(String.format("- **min rank:** %s\n", search.getMinRank().name().toLowerCase()));
      any = true;
    }
    if (search.getMaxRank() != null) {
      w.write(String.format("- **max rank:** %s\n", search.getMaxRank().name().toLowerCase()));
      any = true;
    }
    if (!any) {
      w.write("- the entire dataset (no additional filters)\n");
    }
  }

  /**
   * Builds a repeatable link to the same search in the ChecklistBank web UI.
   */
  static URI searchUrl(URI clbURI, int datasetKey, NameUsageSearchRequest search) {
    if (clbURI == null) {
      return null;
    }
    UriBuilder ub = UriBuilder.fromUri(clbURI).path("dataset").path(String.valueOf(datasetKey)).path("names");
    if (search.hasQ()) {
      ub.queryParam("q", search.getQ());
    }
    if (search.getFilters() != null) {
      for (Map.Entry<NameUsageSearchParameter, Set<Object>> e : search.getFilters().entrySet()) {
        for (Object v : e.getValue()) {
          ub.queryParam(e.getKey().name(), valueToString(v));
        }
      }
    }
    if (search.getContent() != null) {
      for (NameUsageSearchRequest.SearchContent c : search.getContent()) {
        ub.queryParam("content", c.name());
      }
    }
    if (search.getSortBy() != null) {
      ub.queryParam("sortBy", search.getSortBy().name());
    }
    if (search.isReverse()) {
      ub.queryParam("reverse", true);
    }
    if (search.getMinRank() != null) {
      ub.queryParam("minRank", search.getMinRank().name());
    }
    if (search.getMaxRank() != null) {
      ub.queryParam("maxRank", search.getMaxRank().name());
    }
    if (search.getSearchType() != null) {
      ub.queryParam("type", search.getSearchType().name());
    }
    return ub.build();
  }

  private static String valueToString(Object v) {
    if (v == null || v.equals(NameUsageRequest.IS_NULL)) {
      return NameUsageRequest.IS_NULL;
    } else if (v.equals(NameUsageRequest.IS_NOT_NULL)) {
      return NameUsageRequest.IS_NOT_NULL;
    } else if (v instanceof Enum<?> e) {
      // the enum constant name round-trips cleanly through the lenient param parsing and avoids spaces in the URL
      return e.name();
    }
    return v.toString();
  }

  private static String nullToDash(String s) {
    return s == null ? "-" : s;
  }
}
