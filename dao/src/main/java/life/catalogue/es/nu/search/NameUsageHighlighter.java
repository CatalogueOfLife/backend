package life.catalogue.es.nu.search;

import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.search.NameUsageSearchRequest.SearchContent;
import life.catalogue.api.search.NameUsageSearchResponse;
import life.catalogue.api.search.NameUsageWrapper;

import org.gbif.nameparser.api.Authorship;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;

import static life.catalogue.api.search.NameUsageSearchRequest.SearchContent.AUTHORSHIP;
import static life.catalogue.api.search.NameUsageSearchRequest.SearchContent.SCIENTIFIC_NAME;
import static life.catalogue.common.collection.CollectionUtils.isEmpty;
import static life.catalogue.es.nu.NameUsageWrapperConverter.normalizeStrongly;
import static life.catalogue.es.nu.NameUsageWrapperConverter.normalizeWeakly;

/*
 * A DIY highlighter we use in stead of Elasticsearch's highlight capabilities.
 * 
 * The life.catalogue.es.query package does contain the classes to specify and serialize a
 * Elasticsearch-native highlight request, but we currently don't use them. The reason is that the
 * things we want to apply the highlighting to are tucked away within the payload field, which is
 * completely opaque to Elasticsearch.
 * 
 * With respect to scientific names, the highlighting may be imprecise. The highlighting is based on
 * occurences of a normalized Q within the normalized name. The start and end positions are used to
 * insert the <em> tags in the original name, so we take a chance and hope that the name and
 * normalized name are similar enough for the user to understand why some snippet of text gets
 * highlighted.
 */
class NameUsageHighlighter {

  private static final String HIGHLIGHT_BEGIN = "<em class='highlight'>";
  private static final String HIGHLIGHT_END = "</em>";

  private final NameUsageSearchRequest request;
  private final NameUsageSearchResponse response;

  private Pattern pattern; // pattern for authorship
  private Pattern patternWN; // pattern for weakly normalized Q
  private Pattern patternSN; // pattern for strongly normalized Q

  NameUsageHighlighter(NameUsageSearchRequest request, NameUsageSearchResponse response) {
    this.request = request;
    this.response = response;
    Set<SearchContent> sc = request.getContent();
    if (sc.contains(AUTHORSHIP)) {
      pattern = Pattern.compile(Pattern.quote(request.getQ().toLowerCase()));
    }
    if (sc.contains(SCIENTIFIC_NAME)) {
      String qWN = normalizeWeakly(request.getQ());
      String qSN = normalizeStrongly(request.getQ());
      patternWN = Pattern.compile(Pattern.quote(qWN));
      patternSN = qWN.equals(qSN) ? null : Pattern.compile(Pattern.quote(qSN));
    }
  }

  void highlightNameUsages() {
    response.getResult().forEach(this::highlight);
  }

  @VisibleForTesting
  void highlight(NameUsageWrapper nuw) {
    Set<SearchContent> sc = request.getContent();
    if (sc.contains(AUTHORSHIP)) {
      highlightAuthorShip(nuw);
    }
    if (sc.contains(SCIENTIFIC_NAME)) {
      highlightScientificName(nuw);
    }
  }

  private void highlightAuthorShip(NameUsageWrapper nuw) {
    Authorship authorship = nuw.getUsage().getName().getBasionymAuthorship();
    if (authorship != null && !isEmpty(authorship.getAuthors())) {
      authorship.setAuthors(authorship.getAuthors().stream().map(this::highlight).collect(Collectors.toList()));
    }
    authorship = nuw.getUsage().getName().getCombinationAuthorship();
    if (authorship != null && !isEmpty(authorship.getAuthors())) {
      authorship.setAuthors(authorship.getAuthors().stream().map(this::highlight).collect(Collectors.toList()));
    }
  }

  private void highlightScientificName(NameUsageWrapper nuw) {
    String original = nuw.getUsage().getName().getScientificName();
    Matcher matcher = patternWN.matcher(normalizeWeakly(original));
    String highlighted = highlight(original, matcher);
    if (highlighted.length() == original.length() && patternSN != null) {
      // Then no highlighting took place; let's try with the strongly normalized name
      matcher = patternSN.matcher(normalizeStrongly(original));
      highlighted = highlight(original, matcher);
    }
    nuw.getUsage().getName().setScientificName(highlighted);
  }

  private String highlight(String value) {
    if (value == null) {
      return value;
    }
    return highlight(value, pattern.matcher(value.toLowerCase()));
  }

  private static String highlight(String value, Matcher matcher) {
    if (matcher.find()) {
      StringBuilder highlighted = new StringBuilder(50);
      int prevEnd = 0;
      do {
        int start = matcher.start();
        /*
         * For scientific names, we get the begin/end string indexes of matches within the normalized name,
         * and use these to insert <em> tags in the original name. But theoretically the normalized name
         * could be bigger than the original name. Anyhow, to be safe it's best to make zero assumptions
         * regarding how much the normalized name deviates from the original name:
         */
        if (start < value.length()) {
          highlighted.append(value.substring(prevEnd, start)).append(HIGHLIGHT_BEGIN);
          prevEnd = Math.min(value.length(), matcher.end());
          highlighted.append(value.substring(start, prevEnd)).append(HIGHLIGHT_END);
        }
      } while (matcher.find() && prevEnd < value.length());
      if (prevEnd < value.length()) {
        highlighted.append(value.substring(prevEnd));
      }
      return highlighted.toString();
    }
    return value;
  }

}
