package life.catalogue.metadata.zenodo;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.License;
import life.catalogue.common.date.FuzzyDate;
import life.catalogue.parser.LicenseParser;
import life.catalogue.parser.SafeParser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.stream.Collectors;

public class ZenodoParser {
  private static final Logger LOG = LoggerFactory.getLogger(ZenodoParser.class);

  private static final ObjectReader ZENODO_READER;
  static {
    var mapper = ApiModule.MAPPER.copy();
    mapper.setVisibility(mapper.getSerializationConfig().getDefaultVisibilityChecker()
                               .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                               .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                               .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                               .withCreatorVisibility(JsonAutoDetect.Visibility.NONE));
    ZENODO_READER = mapper.readerFor(ZenodoResource.class);
  }

  public static Optional<DatasetWithSettings> parse(InputStream stream) throws IOException {
    if (stream != null) {
      Dataset d = convert(ZENODO_READER.readValue(stream));
      if (d != null) {
        return Optional.of(new DatasetWithSettings(d));
      }
    }
    return Optional.empty();
  }

  private static Dataset convert(ZenodoResource z) {
    if (z != null) {
      Dataset d = new Dataset();
      d.setDoi(z.doi);
      d.setTitle(z.metadata.title);
      d.setVersion(z.metadata.version);
      d.setDescription(z.metadata.description);
      if (z.metadata.license == null) {
        d.setLicense(License.UNSPECIFIED);
      } else {
        d.setLicense(
          SafeParser.parse(LicenseParser.PARSER, z.metadata.license.id).orElse(License.OTHER)
        );
      }
      if (z.metadata.publication_date != null) {
        d.setIssued(new FuzzyDate(z.metadata.publication_date));
      }
      if (z.metadata.contributors != null) {
        d.setContributor(z.metadata.contributors.stream()
                                                .map(ZenodoParser::toClbAgent)
                                                .collect(Collectors.toList())
        );
      }
      if (z.metadata.creators != null) {
        d.setCreator(z.metadata.creators.stream()
                                                .map(ZenodoParser::toClbAgent)
                                                .collect(Collectors.toList())
        );
      }
      d.setNotes(z.metadata.notes);
      if (z.metadata.related_identifiers != null) {
        for (var id : z.metadata.related_identifiers) {
          if (id.identifier != null) {
            if (id.relation.equalsIgnoreCase("isDerivedFrom") && id.resource_type.equalsIgnoreCase("publication-article")) {
              if (id.scheme.equalsIgnoreCase("doi")) {
                try {
                  Citation c = new Citation();
                  c.setDoi(new DOI(id.identifier));
                  d.addSource(c);
                } catch (IllegalArgumentException e) {
                  LOG.info("{} is not a valid DOI", id.identifier);
                }
              }
            }
          }
        }
      }
      return d;
    }
    return null;
  }

  private static Agent toClbAgent(ZenodoResource.Agent agent) {
    if (agent == null || agent.name == null) return null;
    Agent a = Agent.parse(agent.name);
    a.setOrganisation(agent.affiliation);
    a.setOrcid(agent.orcid);
    a.setNote(agent.type);
    return a;
  }

}
