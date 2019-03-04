package org.col.admin.importer.coldp;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import org.col.admin.importer.NeoInserter;
import org.col.admin.importer.NormalizationFailedException;
import org.col.admin.importer.neo.NeoDb;
import org.col.admin.importer.neo.NodeBatchProcessor;
import org.col.admin.importer.reference.ReferenceFactory;
import org.col.admin.jackson.EnumParserSerde;
import org.col.api.datapackage.ColTerm;
import org.col.api.jackson.PermissiveEnumSerde;
import org.col.api.model.Dataset;
import org.col.api.vocab.DataFormat;
import org.col.api.vocab.License;
import org.col.parser.LicenseParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class ColdpInserter extends NeoInserter {

  private static final Logger LOG = LoggerFactory.getLogger(ColdpInserter.class);
  private static final List<String> METADATA_FILENAMES = ImmutableList.of("metadata.yaml", "metadata.yml");
  private static final ObjectMapper OM;
  private static final ObjectReader DATASET_READER;
  static {
    OM = new ObjectMapper(new YAMLFactory())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .registerModule(new JavaTimeModule())
        .registerModule(new ColdpYamlModule());
    DATASET_READER = OM.readerFor(Dataset.class);
  }
  
  private ColdpInterpreter inter;

  public ColdpInserter(NeoDb store, Path folder, ReferenceFactory refFactory) throws IOException {
    super(folder, ColdpReader.from(folder), store, refFactory);
  }
  
  /**
   * Inserts COL data from a source folder into the normalizer store. Before inserting it does a
   * quick check to see if all required files are existing.
   */
  @Override
  public void batchInsert() throws NormalizationFailedException {
    try {
      inter = new ColdpInterpreter(store.getDataset(), reader.getMappingFlags(), refFactory, store);

      // This inserts the plain references from the Reference file with no links to names, taxa or distributions.
      // Links are added afterwards in other methods when a ACEF:ReferenceID field is processed by lookup to the neo store.
      insertEntities(reader, ColTerm.Reference,
          inter::interpretReference,
          store::create
      );

      // name & relations
      insertEntities(reader, ColTerm.Name,
          inter::interpretName,
          store.names()::create
      );
      insertNameRelations(reader, ColTerm.NameRel,
          inter::interpretNameRelations,
          ColTerm.nameID,
          ColTerm.relatedNameID
      );

      // taxa
      insertEntities(reader, ColTerm.Taxon,
          inter::interpretTaxon,
          store.usages()::create
      );
      
      // synonyms
      insertEntities(reader, ColTerm.Synonym,
          inter::interpretSynonym,
          store.usages()::create
      );
  
      // supplementary
      insertTaxonEntities(reader, ColTerm.Description,
          inter::interpretDescription,
          ColTerm.taxonID,
          (t, d) -> t.descriptions.add(d)
      );
      insertTaxonEntities(reader, ColTerm.Distribution,
          inter::interpretDistribution,
          ColTerm.taxonID,
          (t, d) -> t.distributions.add(d)
      );
      insertTaxonEntities(reader, ColTerm.Media,
          inter::interpretMedia,
          ColTerm.taxonID,
          (t, d) -> t.media.add(d)
      );
      insertTaxonEntities(reader, ColTerm.VernacularName,
          inter::interpretVernacular,
          ColTerm.taxonID,
          (t, d) -> t.vernacularNames.add(d)
      );
      
    } catch (RuntimeException e) {
      throw new NormalizationFailedException("Failed to read ColDP files", e);
    }
  }

  @Override
  protected NodeBatchProcessor relationProcessor() {
    return new ColdpRelationInserter(store, inter);
  }

  /**
   * Reads the dataset metadata.yaml and puts it into the store
   */
  @Override
  protected Optional<Dataset> readMetadata() {
    for (String fn : METADATA_FILENAMES) {
      Path metapath = super.folder.resolve(fn);
      if (Files.exists(metapath)) {
        try {
          BufferedReader reader = Files.newBufferedReader(metapath, Charsets.UTF_8);
          Dataset d = DATASET_READER.readValue(reader);
          d.setDataFormat(DataFormat.COLDP);
          // TODO: transform contact ORCIDSs
          return Optional.of(d);
          
        } catch (IOException e) {
          LOG.error("Error reading " + fn, e);
        }
      }
    }
    return Optional.empty();
  }
  
  static  class ColdpYamlModule extends SimpleModule {
    public ColdpYamlModule() {
      super("ColdpYaml");
      EnumParserSerde<License> lserde = new EnumParserSerde<License>(LicenseParser.PARSER);
      addDeserializer(License.class, lserde.new Deserializer());
    }
    
    @Override
    public void setupModule(SetupContext ctxt) {
      // default enum serde
      ctxt.addDeserializers(new PermissiveEnumSerde.PermissiveEnumDeserializers());
      super.setupModule(ctxt);
    }
  }

}
