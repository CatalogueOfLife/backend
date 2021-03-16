package life.catalogue.importer.dwca;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.model.Organisation;
import life.catalogue.api.model.Person;
import life.catalogue.api.vocab.Country;
import life.catalogue.common.date.FuzzyDate;
import life.catalogue.common.io.CharsetDetectingStream;
import life.catalogue.parser.CountryParser;
import life.catalogue.parser.DateParser;
import life.catalogue.parser.LicenseParser;
import life.catalogue.parser.SafeParser;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.units.qual.A;
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
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
              case "associatedParty":
                agent = new Agent();
                break;
              case "ulink":
                String val = parser.getAttributeValue(null, "url");
                if (val != null) {
                  try {
                    url = URI.create(val);
                  } catch (IllegalArgumentException e) {
                    LOG.warn("Invalid ulink URL {}", val);
                    url = null;
                  }
                }
                break;
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
              case "intellectualRights":
                if (url != null) {
                  d.setLicense(LicenseParser.PARSER.parseOrNull(url.toString()));
                }
                break;

              // AGENT PROPS
              case "role":
                agent.role = text(text);
                break;

              case "givenName":
                agent.givenName = text(text);
                break;
              case "surName":
                agent.surName = text(text);
                break;
              case "electronicMailAddress":
                agent.electronicMailAddress = text(text);
                break;
              case "userId":
                agent.userId = text(text);
                break;

              case "organizationName":
                agent.organizationName = text(text);
                break;
              case "country":
                agent.country = text(text);
                break;
              case "city":
                agent.city = text(text);
                break;
              case "postalCode":
                agent.postalCode = text(text);
                break;
              case "administrativeArea":
                agent.administrativeArea = text(text);
                break;
              case "deliveryPoint":
                agent.deliveryPoint = text(text);
                break;
              case "onlineUrl":
                agent.onlineUrl = text(text);
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
                case "geographicDescription":
                  d.setGeographicScope(text(text));
                  break;
                case "creator":
                  agent.role = "CREATOR";
                  addAgent(agent, d.getDataset());
                  break;
                case "contact":
                  agent.role = "CONTACT";
                  addAgent(agent, d.getDataset());
                  break;
                case "associatedParty":
                  addAgent(agent, d.getDataset());
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
                case "confidence":
                  d.setConfidence(integer(text));
                  break;
                case "completeness":
                  d.setCompleteness(integer(text));
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
      consolidate(d);
      return Optional.of(d);
      
    } catch (XMLStreamException e) {
      LOG.error("Failed to parse EML: {}", e.getMessage(), e);
      
    }
    return Optional.empty();
  }

  private static void consolidate(DatasetWithSettings ds){
    Dataset d = ds.getDataset();
    // dedupe orgs, authors and editors
    d.setOrganisations(
      d.getOrganisations().stream().distinct().collect(Collectors.toList())
    );
    d.setAuthors(
      d.getAuthors().stream().distinct().collect(Collectors.toList())
    );
    d.setEditors(
      d.getEditors().stream().distinct().collect(Collectors.toList())
    );
    // parse out organisation departments if seperated by semicolons
    for (Organisation o : d.getOrganisations()) {
      if (o.getDepartment() == null && o.getName() != null) {
        if (o.getName().contains(";")) {
          String[] parts = o.getName().split(";", 2);
          o.setName(parts[0].trim());
          o.setDepartment(parts[1].trim());
        }
      }
    }
  }

  private static void addAgent(Agent agent, Dataset d){
    // require a role!
    if (agent.role == null) return;
    switch (agent.role.toUpperCase().trim()) {
      case "CONTACT":
      case "POINTOFCONTACT":
        agent.person().ifPresent(d::setContact);
        agent.organisation().ifPresent(d.getOrganisations()::add);
        break;
      case "AUTHOR":
      case "CREATOR":
      case "CONTENTPROVIDER":
        agent.person().ifPresent(d.getAuthors()::add);
        agent.organisation().ifPresent(d.getOrganisations()::add);
        break;
      case "EDITOR":
      case "CUSTODIANSTEWARD":
        agent.person().ifPresent(d.getEditors()::add);
        agent.organisation().ifPresent(d.getOrganisations()::add);
        break;
      default:
        LOG.debug("Ignore EML agent role {}", agent.role);
    }
  }

  private static String text(StringBuilder text) {
    return text == null || text.length() < 1 ? null : text.toString();
  }

  private static Integer integer(StringBuilder text) {
    try {
      return text == null || text.length() < 1 ? null : Integer.parseInt(text.toString());
    } catch (NumberFormatException e) {
      LOG.debug("No integer: {}", text.toString());
      return null;
    }
  }

  private static FuzzyDate date(StringBuilder text) {
    return SafeParser.parse(DateParser.PARSER, text.toString()).orNull();
  }
  
  static class Agent {
    private static final Pattern ORCID = Pattern.compile("^(?:https?://orcid.org/|orcid:)?(\\d{4}-\\d{4}-\\d{4}-\\d{4})\\s*$", Pattern.CASE_INSENSITIVE);
    public String role;
    public String onlineUrl;
    public String electronicMailAddress;

    public String givenName;
    public String surName;
    public String url;
    public String userId;

    public String organizationName;
    public String country;
    public String city;
    public String postalCode;
    public String administrativeArea;
    public String deliveryPoint;

    Optional<Person> person() {
      if (givenName != null || surName != null || electronicMailAddress != null) {
        Person p = new Person();
        p.setGivenName(givenName);
        p.setFamilyName(surName);
        p.setEmail(electronicMailAddress);
        if (userId != null) {
          // verify ORCID pattern
          Matcher m = ORCID.matcher(userId);
          if (m.find()) {
            p.setOrcid(m.group(1));
          } else {
            LOG.debug("UserID {} is not an ORCID", userId);
          }
        }
        return Optional.of(p);
      }
      return Optional.empty();
    }

    Optional<Organisation> organisation() {
      if (organizationName != null) {
        Organisation o = new Organisation();
        o.setDepartment(null);
        o.setName(organizationName);
        o.setCity(city);
        o.setState(administrativeArea);
        if (country != null) {
          o.setCountry(CountryParser.PARSER.parseOrNull(country));
        }
        return Optional.of(o);
      }
      return Optional.empty();
    }
  }
  
}
