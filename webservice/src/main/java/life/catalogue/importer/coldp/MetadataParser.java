package life.catalogue.importer.coldp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.*;
import com.google.common.collect.ImmutableList;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.model.Organisation;
import life.catalogue.api.model.Person;
import life.catalogue.importer.dwca.EmlParser;
import life.catalogue.jackson.YamlMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

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
          setContact(parsePerson(contacts.get(0)));
        }
      } else {
        setContact(parsePerson(contact));
      }
    }

    @JsonProperty("authors")
    public void setAuthorsAlt(Object authors) {
      super.setAuthors(parsePersons(authors));
    }

    @JsonProperty("editors")
    public void setEditorsAlt(Object editors) {
      super.setEditors(parsePersons(editors));
    }

    @JsonProperty("authorsAndEditors")
    public void setAuthorsAndEditors(Object authorsAndEditors) {
      setAuthorsAlt(authorsAndEditors);
    }

    @JsonProperty("authorsUnparsed")
    public void setAuthorsUnparsed(Object authors) {
      setAuthorsAlt(authors);
    }

    @JsonProperty("organisations")
    public void setOrgsAlt(Object obj) {
      super.setOrganisations((parseOrganisations(obj)));
    }

    List<Organisation> parseOrganisations(Object obj) {
      if (obj != null) {
        if (obj instanceof List) {
          List<Organisation> orgs = new ArrayList<>();
          for (Object ob : (List) obj) {
            Organisation o = parseOrg(ob);
            if (o != null) {
              orgs.add(o);
            }
          }
          return orgs;

        } else if (obj instanceof String){
          return split((String)obj).stream()
            .map(this::parseOrg)
            .collect(Collectors.toList());
        } else {
          Organisation o = parseOrg(obj);
          if (o != null) return List.of(o);
        }
      }
      return null;
    }

    List<Person> parsePersons(Object obj) {
      if (obj != null) {
        if (obj instanceof List) {
          List<Person> persons = new ArrayList<>();
          for (Object o : (List) obj) {
            Person p = parsePerson(o);
            if (p != null) {
              persons.add(p);
            }
          }
          return persons;

        } else if (obj instanceof String){
          return split((String)obj).stream()
            .map(this::parsePerson)
            .collect(Collectors.toList());
        } else {
          Person p = parsePerson(obj);
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

    Person parsePerson(Object obj) {
      if (obj != null) {
        if (obj instanceof Person) {
          return (Person)obj;

        } else if (obj instanceof String) {
          return new Person((String)obj);

        } else if (obj instanceof Map) {
          return YamlMapper.MAPPER.convertValue(obj, Person.class);

        }
      }
      return null;
    }

    Organisation parseOrg(Object org) {
      if (org != null) {
        if (org instanceof Organisation) {
          return (Organisation)org;
        } else if (org instanceof String) {
          return new Organisation((String)org);
        } else if (org instanceof Map) {
          return YamlMapper.MAPPER.convertValue(org, Organisation.class);
        }
      }
      return null;
    }


    @JsonProperty("taxonomicScope")
    public void setTaxonomicScope(String scope) {
      setGroup(scope);
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
