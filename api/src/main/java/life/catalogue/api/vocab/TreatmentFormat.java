package life.catalogue.api.vocab;

import life.catalogue.common.ws.MoreMediaTypes;

import jakarta.ws.rs.core.MediaType;

public enum TreatmentFormat {

  PLAIN_TEXT(MediaType.TEXT_PLAIN_TYPE),
  MARKDOWN(MoreMediaTypes.TEXT_MARKDOWN_TYPE),
  XML(MediaType.TEXT_XML_TYPE),
  HTML(MediaType.TEXT_HTML_TYPE),
  TAX_PUB(MediaType.TEXT_XML_TYPE),
  TAXON_X(MediaType.TEXT_XML_TYPE),
  RDF(MediaType.TEXT_XML_TYPE);

  private final MediaType type;

  TreatmentFormat(MediaType type) {
    this.type = type;
  }

  public MediaType getMediaType() {
    return type;
  }
}
