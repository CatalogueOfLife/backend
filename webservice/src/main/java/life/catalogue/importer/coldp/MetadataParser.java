package life.catalogue.importer.coldp;

import life.catalogue.api.model.Agent;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.importer.dwca.EmlParser;
import life.catalogue.jackson.YamlMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
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

  static class YamlDataset extends DatasetWithSettings {

    @JsonProperty("contact")
    public void setContact(Object contact) {
      if (contact instanceof List) {
        List<?> contacts = (List)contact;
        if (!contacts.isEmpty()) {
          setContact(parseAgent(contacts.get(0)));
        }
      } else {
        setContact(parseAgent(contact));
      }
    }

    @JsonProperty("creator")
    public void setCreatorAlt(Object creators) {
      super.setCreator(parseAgents(creators));
    }

    @JsonProperty("creators")
    public void setCreators(Object creators) {
      super.setCreator(parseAgents(creators));
    }

    @JsonProperty("authors")
    public void setAuthorsAlt(Object authors) {
      super.setCreator(parseAgents(authors));
    }

    @JsonProperty("editor")
    public void setEditorAlt(Object editors) {
      super.setEditor(parseAgents(editors));
    }

    @JsonProperty("editors")
    public void setEditors(Object editors) {
      super.setEditor(parseAgents(editors));
    }

    @JsonProperty("organisations")
    public void setOrganisations(Object orgs) {
      super.setDistributor(parseAgents(orgs));
    }

    List<Agent> parseAgents(Object obj) {
      if (obj != null) {
        if (obj instanceof List) {
          List<Agent> persons = new ArrayList<>();
          for (Object o : (List) obj) {
            Agent p = parseAgent(o);
            if (p != null) {
              persons.add(p);
            }
          }
          return persons;

        } else if (obj instanceof String){
          return split((String)obj).stream()
            .map(this::parseAgent)
            .collect(Collectors.toList());
        } else {
          Agent p = parseAgent(obj);
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

    Agent parseAgent(Object obj) {
      if (obj != null) {
        if (obj instanceof Agent) {
          return (Agent)obj;

        } else if (obj instanceof String) {
          return new Agent((String)obj);

        } else if (obj instanceof Map) {
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
      try {
        setUrl(URI.create(website));
      } catch (IllegalArgumentException e) {
        LOG.warn("Ignore bad url {}", website);
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
      if (d.getDescription() != null) {
        d.setDescription(d.getDescription().trim());
      }
      return Optional.of(d);
  }
    return Optional.empty();
  }

}
