package life.catalogue.importer.coldp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.IntSet;
import life.catalogue.api.jackson.FastutilsSerde;
import life.catalogue.api.jackson.PermissiveEnumSerde;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.model.Organisation;
import life.catalogue.api.model.Person;
import life.catalogue.api.vocab.Country;
import life.catalogue.api.vocab.License;
import life.catalogue.importer.dwca.EmlParser;
import life.catalogue.jackson.EnumParserSerde;
import life.catalogue.jackson.YamlMapper;
import life.catalogue.parser.CountryParser;
import life.catalogue.parser.LicenseParser;
import org.gbif.dwc.terms.TermFactory;
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

    @JsonProperty("organisations")
    public void setOrgsAlt(List<?> orgs) {
      List<Organisation> organisations = new ArrayList<>();
      if (orgs != null) {
        for (Object org : orgs) {
          if (org != null) {
            if (org instanceof Organisation) {
              organisations.add((Organisation)org);
            } else if (org instanceof String) {
              organisations.add(new Organisation((String)org));
            } else if (org instanceof Map) {
              organisations.add(YamlMapper.MAPPER.convertValue(org, Organisation.class));
            }
          }
        }
      }
      setOrganisations(organisations);
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

        } else {
          Person p = parsePerson(obj);
          if (p != null) return List.of(p);
        }
      }
      return null;
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

    @JsonProperty("authorsAndEditors")
    public void setAuthorsAndEditors(List<Person> authorsAndEditors) {
      if (authorsAndEditors == null || authorsAndEditors.isEmpty()) {
        setAuthors(Collections.emptyList());
      } else {
        setAuthors(authorsAndEditors);
      }
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
