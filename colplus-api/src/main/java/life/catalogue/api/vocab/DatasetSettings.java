package life.catalogue.api.vocab;

public enum DatasetSettings {

    /**
     * When importing data from text files this overrides
     * the field delimiter character used
     */
    CSV_DELIMITER,

    /**
     * When importing data from text files this overrides
     * the quote character used
     */
    CSV_QUOTE,

    /**
	 * When importing data from text files this overrides
     * the single character used for escaping quotes inside an already quoted value.
     * For example '"' for CSV
     */
    CSV_QUOTE_ESCAPE
}
