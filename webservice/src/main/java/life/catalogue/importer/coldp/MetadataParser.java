package life.catalogue.importer.coldp;

import life.catalogue.api.model.Agent;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.dao.DaoUtils;
import life.catalogue.importer.dwca.EmlParser;
import life.catalogue.jackson.YamlMapper;
import life.catalogue.parser.DateParser;
import life.catalogue.parser.SafeParser;
import life.catalogue.parser.UriParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectReader;
import com.google.common.collect.ImmutableList;

/**
 * ColDP metadata parser that falls back to EML if no YAML metadata is found.
 */
public class MetadataParser {
  private static final Logger LOG = LoggerFactory.getLogger(MetadataParser.class);
  private static final List<String> METADATA_FILENAMES = ImmutableList.of("metadata.yaml", "metadata.yml");
  private static final List<String> EML_FILENAMES = ImmutableList.of("eml.xml", "metadata.xml");
  private static final ObjectReader DATASET_YAML_READER;
  static {
    DATASET_YAML_READER = YamlMapper.MAPPER.readerFor(YamlDataset.class);
  }

  /**
   * Wrapper class that allows us to support multiple names for the same property
   * and also setting both simple unparsed strings and complex agents.
   */
  static class YamlDataset extends DatasetWithSettings {

    @Override
    public void setDescription(String description) {
      super.setDescription(DaoUtils.stripHtml(description));
    }

    @Override
    public void setTitle(String title) {
      super.setTitle(DaoUtils.stripHtml(title));
    }

    @JsonProperty("contact")
    public void setContact(Object contact) {
      if (contact instanceof List) {
        List<?> contacts = (List)contact;
        if (!contacts.isEmpty()) {
          setContact(parseAgent(contacts.get(0), false));
        }
      } else {
        setContact(parseAgent(contact, false));
      }
    }

    @JsonProperty("creator")
    public void setCreatorAlt(Object creators) {
      super.setCreator(parseAgents(creators, false));
    }

    @JsonProperty("creators")
    public void setCreators(Object creators) {
      super.setCreator(parseAgents(creators, false));
    }

    @JsonProperty("author")
    public void setAuthor(Object authors) {
      super.setCreator(parseAgents(authors, false));
    }

    @JsonProperty("authors")
    public void setAuthorsAlt(Object authors) {
      super.setCreator(parseAgents(authors, false));
    }

    @JsonProperty("editor")
    public void setEditorAlt(Object editors) {
      super.setEditor(parseAgents(editors, false));
    }

    @JsonProperty("editors")
    public void setEditors(Object editors) {
      super.setEditor(parseAgents(editors, false));
    }

    @JsonProperty("organisations")
    public void setOrganisations(Object orgs) {
      super.setContributor(parseAgents(orgs, true));
    }

    List<Agent> parseAgents(Object obj, final boolean acceptNameAsOrganisation) {
      if (obj != null) {
        if (obj instanceof List) {
          List<Agent> persons = new ArrayList<>();
          for (Object o : (List) obj) {
            Agent p = parseAgent(o, acceptNameAsOrganisation);
            if (p != null) {
              persons.add(p);
            }
          }
          return persons;

        } else if (obj instanceof String){
          return split((String)obj).stream()
            .map(x -> parseAgent(x, acceptNameAsOrganisation))
            .collect(Collectors.toList());
        } else {
          Agent p = parseAgent(obj, acceptNameAsOrganisation);
          if (p != null) return List.of(p);
        }
      }
      return null;
    }

    List<?> split(String x) {
      if (x == null) return Collections.emptyList();
      if (x.contains(";")) {
        return Arrays.stream(x.split(";")).map(String::trim).collect(Collectors.toList());
      }
      return List.of(x.trim());
    }

    Agent parseAgent(Object obj, final boolean acceptNameAsOrganisation) {
      if (obj != null) {
        if (obj instanceof Agent) {
          return (Agent)obj;

        } else if (obj instanceof String) {
          return Agent.parse((String)obj);

        } else if (obj instanceof Map) {
          // allow name as alternative to organisation to support older YAML format
          if (acceptNameAsOrganisation) {
            Map<Object, Object> map = (Map<Object, Object>) obj;
            if (map.containsKey("name") && !map.containsKey("organisation")) {
              map.put("organisation", map.get("name"));
            }
          }
          return YamlMapper.MAPPER.convertValue(obj, Agent.class);
        }
      }
      return null;
    }

    @JsonProperty("group")
    public void setGroup(String group) {
      setTaxonomicScope(group);
    }

    @JsonProperty("website")
    public void setWebsite(String website) {
      setUrl(UriParser.parseDontThrow(website));
    }

    @JsonProperty("released")
    public void setReleased(String issued) {
      if (issued == null) {
        super.setIssued(null);
      } else {
        super.setIssued(SafeParser.parse(DateParser.PARSER, issued).orNull());
      }
    }
  }

  /**
   * Reads the dataset metadata.yaml or metadata.yml from a given folder.
   * In case of parsing errors an empty optional is returned.
   */
  public static Optional<DatasetWithSettings> readMetadata(Path dir) {
    for (String fn : METADATA_FILENAMES) {
      Path metapath = dir.resolve(fn);
      if (Files.exists(metapath)) {
        try {
          return readMetadata(Files.newInputStream(metapath));
        } catch (Exception e) {
          LOG.error("Error reading metadata from " + fn, e);
        }
      }
    }
    // also try with EML if none is found
    for (String fn : EML_FILENAMES) {
      Path eml = dir.resolve(fn);
      if (Files.exists(eml)) {
        try {
          return EmlParser.parse(eml);
        } catch (IOException e) {
          LOG.error("Error reading EML file " + fn, e);
        }
      }
    }

    return Optional.empty();
  }
  
  public static Optional<DatasetWithSettings> readMetadata(InputStream stream) throws IOException {
    if (stream != null) {
      DatasetWithSettings d = DATASET_YAML_READER.readValue(stream);
      return Optional.of(d);
  }
    return Optional.empty();
  }

}
