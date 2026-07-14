package life.catalogue.common.csl;

import life.catalogue.api.model.Agent;
import life.catalogue.api.model.Dataset;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import de.undercouch.citeproc.csl.CSLItemData;
import de.undercouch.citeproc.csl.CSLItemDataBuilder;
import de.undercouch.citeproc.csl.CSLName;
import de.undercouch.citeproc.csl.CSLNameBuilder;
import de.undercouch.citeproc.csl.CSLType;

/**
 * Builds citeproc {@link CSLItemData} from the COL {@link Dataset} model.
 * Relocated from the former {@code Dataset.toCSL()/toCSLBuilder()} to keep the api model
 * citeproc-free.
 */
public class DatasetCitationConverter {

  public static CSLItemData toCSL(Dataset d) {
    return toCSLBuilder(d).build();
  }

  public static CSLItemDataBuilder toCSLBuilder(Dataset d) {
    CSLItemDataBuilder builder = new CSLItemDataBuilder();
    builder
      .type(CSLType.DATASET)
      .shortTitle(d.getAlias())
      .version(d.getVersion())
      .ISSN(d.getIssn());
    if (d.getKey() != null) {
      builder.id(d.getKey().toString());
    }
    if (d.getKeyword() != null && !d.getKeyword().isEmpty()) {
      builder.keyword(String.join(", ", d.getKeyword()));
    }
    if (d.getPublisher() != null && d.getPublisher().getOrganisation() != null) {
      builder
        .publisher(d.getPublisher().getOrganisation())
        .publisherPlace(d.getPublisher().getAddress());
    }
    if (d.getDoi() != null) {
      builder.DOI(d.getDoi().toString());
    }
    if (d.getUrl() != null) {
      builder.URL(d.getUrl().toString());
    }
    if (d.getIssued() != null) {
      builder.issued(CitationConverter.toDate(d.getIssued()));
    }
    if (d.getContainerTitle() != null) {
      // we change the title of a source to append the source version which otherwise would be lost
      StringBuilder chapter = new StringBuilder();
      chapter.append(d.getTitle());
      if (d.getVersion() != null) {
        chapter
          .append(" (version ")
          .append(d.getVersion())
          .append(")");
      }
      builder
        .type(CSLType.CHAPTER)
        .title(chapter.toString())
        .author(toNamesArray(unique(merge(d.getCreator(), d.getEditor()))))
        .containerTitle(d.getContainerTitle())
        .containerAuthor(toNamesArray(d.getContainerCreator()))
        .version(d.getContainerVersion());
      if (d.getContainerIssued() != null) {
        builder.issued(CitationConverter.toDate(d.getContainerIssued()));
      }
      if (d.getContainerPublisher() != null && d.getContainerPublisher().getOrganisation() != null) {
        builder
          .publisher(d.getContainerPublisher().getOrganisation())
          .publisherPlace(d.getContainerPublisher().getAddress());
      }
    } else {
      builder
        .title(d.getTitle())
        .author(toNamesArray(d.getCreator()))
        .editor(toNamesArray(d.getEditor()));
    }
    // no license, distributor, contributor
    return builder;
  }

  @SafeVarargs
  private static List<Agent> merge(List<Agent>... names) {
    List<Agent> all = new ArrayList<>();
    for (List<Agent> n : names) {
      if (n != null && !n.isEmpty()) {
        all.addAll(n);
      }
    }
    return all;
  }

  private static List<Agent> unique(List<Agent> names) {
    final Set<String> seen = ConcurrentHashMap.newKeySet();
    names.removeIf(n -> {
      if (n != null && n.getName() != null && !seen.contains(n.getName())) {
        seen.add(n.getName());
        return false;
      }
      return true;
    });
    return names;
  }

  private static CSLName[] toNamesArray(List<Agent> names) {
    if (names == null || names.isEmpty()) return null;
    return names.stream()
                .map(DatasetCitationConverter::toCSL)
                .filter(Objects::nonNull)
                .collect(Collectors.toList())
                .toArray(CSLName[]::new);
  }

  private static CSLName toCSL(Agent a) {
    if (a.isPerson()) {
      return new CSLNameBuilder()
        .given(StringUtils.trimToNull(a.getGiven()))
        .family(StringUtils.trimToNull(a.getFamily()))
        .isInstitution(false)
        .build();
    } else if (a.isOrganisation()) {
      return new CSLNameBuilder()
        .family(StringUtils.trimToNull(a.getOrganisation()))
        .isInstitution(true)
        .build();
    }
    return null;
  }
}
