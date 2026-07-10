package life.catalogue.common.csl;

import life.catalogue.api.model.Citation;
import life.catalogue.api.model.CitationFormatter;
import life.catalogue.api.model.Dataset;

/**
 * The citeproc-backed {@link CitationFormatter} implementation. Registered once at application
 * startup (and in downstream test bases) so the slim api model can render citation strings via
 * the hook without depending on citeproc itself.
 */
public class CslCitationFormatter implements CitationFormatter {
  public String citationHtml(Citation c) { return CslUtil.buildCitationHtml(CitationConverter.toCSL(c)); }
  public String citationText(Citation c) { return CslUtil.buildCitation(CitationConverter.toCSL(c)); }
  public String citationHtml(Dataset d) { return CslUtil.buildCitationHtml(DatasetCitationConverter.toCSL(d)); }
  public String citationText(Dataset d) { return CslUtil.buildCitation(DatasetCitationConverter.toCSL(d)); }
}
