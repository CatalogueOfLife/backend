package life.catalogue.api.vocab;

import life.catalogue.common.ws.MoreMediaTypes;

import jakarta.ws.rs.core.MediaType;

public enum TreatmentFormat {

  PLAIN_TEXT(MediaType.TEXT_PLAIN_TYPE, "txt"),
  MARKDOWN(MoreMediaTypes.TEXT_MARKDOWN_TYPE, "md"),
  XML(MediaType.TEXT_XML_TYPE, "xml"),
  HTML(MediaType.TEXT_HTML_TYPE, "html"),
  TAX_PUB(MediaType.TEXT_XML_TYPE, "xml"),
  TAXON_X(MediaType.TEXT_XML_TYPE, "xml"),
  RDF(MediaType.TEXT_XML_TYPE, "rdf");

  private final MediaType type;
  private final String suffix;

  TreatmentFormat(MediaType type, String suffix) {
    this.type = type;
    this.suffix = suffix;
  }

  public MediaType getMediaType() {
    return type;
  }

  public String getFileSuffix() {
    return suffix;
  }
}
