package life.catalogue.importer.dwca;

import life.catalogue.api.model.*;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.common.date.FuzzyDate;
import life.catalogue.common.io.CharsetDetectingStream;
import life.catalogue.importer.neo.ReferenceStore;
import life.catalogue.importer.reference.ReferenceFactory;
import life.catalogue.parser.CountryParser;
import life.catalogue.parser.DateParser;
import life.catalogue.parser.LicenseParser;
import life.catalogue.parser.SafeParser;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.annotation.meta.setter;

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
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 *
 */
public class EmlParser {
  private static final Logger LOG = LoggerFactory.getLogger(EmlParser.class);
  private static final Pattern INTSTITUTE_PATTERN = Pattern.compile("\\b(Instituu?te?|Museum|Academ[yi]|Universi[dt])", Pattern.CASE_INSENSITIVE);
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
      EmlAgent agent = new EmlAgent();
      URI url = null;
      String identifier = null;
      int event;
      
      while ((event = parser.next()) != XMLStreamConstants.END_DOCUMENT) {
        switch (event) {
          case XMLStreamConstants.START_ELEMENT:
            text = new StringBuilder();
            identifier = null;
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
              case "personnel":
                agent = new EmlAgent();
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
              case "citation":
                identifier = parser.getAttributeValue(null, "identifier");
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
                  d.setUrl(url);
                  break;
                case "pubDate":
                  FuzzyDate fuzzy = date(text);
                  if (fuzzy != null) {
                    d.setIssued(fuzzy);
                  }
                  break;
                case "geographicDescription":
                  d.setGeographicScope(text(text));
                  break;
                case "generalTaxonomicCoverage":
                  d.setTaxonomicScope(text(text));
                  break;
                case "creator":
                  agent.role = "CREATOR";
                  addAgent(agent, d.getDataset());
                  break;
                case "contact":
                  agent.role = "CONTACT";
                  addAgent(agent, d.getDataset());
                  break;
                case "publisher":
                  agent.role = "PUBLISHER";
                  addAgent(agent, d.getDataset());
                  break;
                case "associatedParty":
                case "personnel":
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
                case "confidence":
                  d.setConfidence(integer(text));
                  break;
                case "completeness":
                  d.setCompleteness(integer(text));
                  break;
                case "citation":
                  Citation cite = new Citation();
                  cite.setTitle(text.toString());
                  d.getDataset().addSource(cite);
                  if (identifier != null) {
                    var opt = DOI.parse(identifier);
                    if (opt.isPresent()) {
                      cite.setDoi(opt.get());
                      cite.setId(cite.getDoi().getDoiName());
                    } else {
                      try {
                        URI link = URI.create(identifier);
                        cite.setUrl(link.toString());
                      } catch (IllegalArgumentException e) {
                        LOG.debug("No URI identifier {}, place it in notes", identifier);
                        cite.setNote(identifier);
                      }
                    }
                  }
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
    // dedupe agents
    dedupe(d.getCreator(), d::setCreator);
    dedupe(d.getEditor(), d::setEditor);
    dedupe(d.getContributor(), d::setContributor);
  }
  private static void dedupe(List<Agent> agents, Consumer<List<Agent>> setter){
    if (agents != null) {
      agents = agents.stream().distinct().collect(Collectors.toList());
      // parse out organisation department if separated by semicolons
      for (Agent a : agents) {
        if (a.getDepartment() == null && a.getOrganisation() != null) {
          if (a.getOrganisation().contains(";")) {
            String[] parts = a.getOrganisation().split(";", 2);
            // test for common intitution terms, otherwise assume organisation is first
            String org = parts[0].trim();
            String dep = parts[1].trim();
            if (!isInstitute(org) && isInstitute(dep)) {
              org = dep;
              dep = parts[1].trim();
            }
            a.setOrganisation(org);
            a.setDepartment(dep);
          }
        }
      }
      setter.accept(agents);
    }
  }

  private static boolean isInstitute(String x) {
    return INTSTITUTE_PATTERN.matcher(x).find();
  }

  private static void addAgent(EmlAgent agent, Dataset d){
    // require a role!
    if (agent.role == null) return;
    switch (agent.role.toUpperCase().trim()) {
      case "CONTACT":
      case "POINTOFCONTACT":
        agent.agent(false).ifPresent(d::setContact);
        break;
      case "PUBLISHER":
        agent.agent(false).ifPresent(d::setPublisher);
        break;
      case "AUTHOR":
      case "CREATOR":
        agent.agent(false).map(EmlParser::nullIfNoName).ifPresent(d::addCreator);
        break;
      case "EDITOR":
        agent.agent(false).map(EmlParser::nullIfNoName).ifPresent(d::addEditor);
        break;
      default:
        agent.agent(true).map(EmlParser::nullIfNoName).ifPresent(d::addContributor);
        break;
    }
  }

  private static Agent nullIfNoName(Agent p) {
    if (p.getName()==null) {
      return null;
    }
    return p;
  }

  private static String text(StringBuilder text) {
    return text == null || text.length() < 1 ? null : StringUtils.trimToNull(text.toString());
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
  
  static class EmlAgent {
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

    Optional<Agent> agent(boolean inclRole) {
      if (givenName != null || surName != null || organizationName != null || electronicMailAddress != null) {
        Agent p = new Agent();
        p.setGiven(givenName);
        p.setFamily(surName);
        if (userId != null) {
          // verify ORCID pattern
          Matcher m = ORCID.matcher(userId);
          if (m.find()) {
            p.setOrcid(m.group(1));
          } else {
            LOG.debug("UserID {} is not an ORCID", userId);
          }
        }
        p.setDepartment(null);
        p.setOrganisation(organizationName);
        p.setCity(city);
        p.setState(administrativeArea);
        if (country != null) {
          p.setCountry(CountryParser.PARSER.parseOrNull(country));
        }
        p.setEmail(electronicMailAddress);
        p.setUrl(ObjectUtils.coalesce(onlineUrl, url));
        if (inclRole) {
          p.setNote(role);
        }
        return Optional.of(p);
      }
      return Optional.empty();
    }
  }
  
}
