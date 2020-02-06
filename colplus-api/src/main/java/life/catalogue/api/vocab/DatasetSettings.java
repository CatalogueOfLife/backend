package life.catalogue.api.vocab;

import org.gbif.nameparser.api.NomCode;

public enum DatasetSettings {


    /**
     * When importing data from text files this overrides
     * the field delimiter character used
     */
    CSV_DELIMITER(String.class),

    /**
     * When importing data from text files this overrides
     * the quote character used
     */
    CSV_QUOTE(String.class),

    /**
	 * When importing data from text files this overrides
     * the single character used for escaping quotes inside an already quoted value.
     * For example '"' for CSV
     */
    CSV_QUOTE_ESCAPE(String.class),

    /**
     * Overrides the gazetteer standard to use in all distribution interpretations for the dataset.
     */
    DISTRIBUTION_GAZETTEER(Gazetteer.class),

    NOMENCLATURAL_CODE(NomCode.class),

    /**
     * Setting that will inform the importer to rematch all decisions (decisions sensu strictu but also sectors and estimates)
     * Defaults to false
     */
    REMATCH_DECISIONS(Boolean.class);

    private final Class type;

    public Class getType() {
        return type;
    }

    public boolean isEnum() {
        return type.isEnum();
    }

    DatasetSettings(Class type) {
        this.type = type;
    }

}
