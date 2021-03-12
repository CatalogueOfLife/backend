package life.catalogue.importer.dwca;

import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.model.Organisation;
import life.catalogue.api.model.Person;
import life.catalogue.common.date.FuzzyDate;
import life.catalogue.common.io.CharsetDetectingStream;
import life.catalogue.parser.DateParser;
import life.catalogue.parser.SafeParser;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 *
 */
public class EmlParser {
  private static final Logger LOG = LoggerFactory.getLogger(EmlParser.class);
  private static final XMLInputFactory factory;
  
  static {
    factory = XMLInputFactory.newInstance();
  }

  public static Optional<DatasetWithSettings> parse(Path file) throws IOException {
    CharsetDetectingStream cds = CharsetDetectingStream.create(Files.newInputStream(file));
    return parse(cds, cds.getCharset());
  }

  public static Optional<DatasetWithSettings> parse(InputStream stream) throws IOException {
    CharsetDetectingStream cds = CharsetDetectingStream.create(stream);
    return parse(cds, cds.getCharset());
  }

  public static Optional<DatasetWithSettings> parse(InputStream stream, Charset encoding) {
    try {
      XMLStreamReader parser = factory.createXMLStreamReader(stream, encoding.name());
      
      final DatasetWithSettings d = new DatasetWithSettings();
      boolean isDataset = false;
      boolean isProject = false;
      boolean isAdditionalMetadata = false;
      StringBuilder text = null;
      StringBuilder para = new StringBuilder();
      Agent agent = new Agent();
      URI url = null;
      int event;
      
      while ((event = parser.next()) != XMLStreamConstants.END_DOCUMENT) {
        switch (event) {
          case XMLStreamConstants.START_ELEMENT:
            text = new StringBuilder();
            switch (parser.getLocalName()) {
              case "dataset":
                isDataset = true;
                break;
              case "additionalMetadata":
                isAdditionalMetadata = true;
                break;
              case "project":
                isProject = true;
                break;
              case "abstract":
                para = new StringBuilder();
                break;
              case "creator":
              case "metadataProvider":
              case "contact":
                agent = new Agent();
            }
            break;
          
          case XMLStreamConstants.END_ELEMENT:
            // first general instructions
            switch (parser.getLocalName()) {
              case "dataset":
                isDataset = false;
                break;
              case "additionalMetadata":
                isAdditionalMetadata = false;
                break;
              case "project":
                isProject = false;
                break;
              case "para":
                if (para.length() > 0) {
                  para.append("\n");
                }
                para.append(text);
                break;
              case "url":
                try {
                  url = URI.create(text.toString());
                } catch (IllegalArgumentException e) {
                  LOG.warn("Invalid URL {}", text.toString());
                  url = null;
                }
                break;
              // AGENT PROPS
              case "givenName":
                agent.givenName = text(text);
                break;
              case "surName":
                agent.surName = text(text);
                break;
              case "organizationName":
                agent.organizationName = text(text);
                break;
              case "electronicMailAddress":
                agent.electronicMailAddress = text(text);
                break;
              case "userId":
                agent.userId = text(text);
                break;
            }

            if (isDataset) {
              switch (parser.getLocalName()) {
                case "title":
                  if (!isProject) {
                    d.setTitle(text(text));
                  }
                  break;
                case "shortName":
                  if (!isProject) {
                    d.setAlias(text(text));
                  }
                  break;
                case "abstract":
                  d.setDescription(para.toString());
                  break;
                case "distribution":
                  d.setWebsite(url);
                  break;
                case "pubDate":
                  FuzzyDate fuzzy = date(text);
                  if (fuzzy != null) {
                    d.setReleased(fuzzy.toLocalDate());
                  }
                  break;
                case "creator":
                  agent.person().ifPresent(d.getAuthors()::add);
                  break;
                case "contact":
                  agent.person().ifPresent(d::setContact);
                  break;
              }
            }
            if (isAdditionalMetadata) {
              switch (parser.getLocalName()) {
                case "resourceLogoUrl":
                  try {
                    d.setLogo(URI.create(text.toString()));
                  } catch (IllegalArgumentException e) {
                    LOG.warn("Invalid logo URL {}", text.toString());
                  }
                  break;
                case "citation":
                  d.setCitation(text(text));
                  break;
              }
            }
            break;
            
          case XMLStreamConstants.CHARACTERS:
            if (isDataset || isAdditionalMetadata || isProject) {
              String x = StringUtils.normalizeSpace(parser.getText());
              if (!StringUtils.isBlank(x)) {
                text.append(x.trim());
              }
            }
            break;
        }
      }
      parser.close();
      return Optional.of(d);
      
    } catch (XMLStreamException e) {
      LOG.error("Failed to parse EML: {}", e.getMessage(), e);
      
    }
    return Optional.empty();
  }
  
  private static String text(StringBuilder text) {
    return text == null || text.length() < 1 ? null : text.toString();
  }
  
  private static FuzzyDate date(StringBuilder text) {
    return SafeParser.parse(DateParser.PARSER, text.toString()).orNull();
  }
  
  static class Agent {
    public String givenName;
    public String surName;
    public String organizationName;
    public String electronicMailAddress;
    public String url;
    public String userId;

    Optional<Person> person() {
      if (givenName != null || surName != null || electronicMailAddress != null || userId != null) {
        Person p = new Person();
        p.setGivenName(givenName);
        p.setFamilyName(surName);
        p.setEmail(electronicMailAddress);
        p.setOrcid(userId);
        return Optional.of(p);
      }
      return Optional.empty();
    }

    Optional<Organisation> organisation() {
      if (givenName != null || surName != null || electronicMailAddress != null || userId != null) {
        Organisation o = new Organisation();
        o.setDepartment(null);
        o.setName(organizationName);
        o.setCity(null);
        o.setState(null);
        o.setCountry(null);
        return Optional.of(o);
      }
      return Optional.empty();
    }
  }
  
}
