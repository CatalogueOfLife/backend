package life.catalogue.printer.diff;

/**
 * A contiguous segment of a changed name. EQUAL text is unchanged; DELETE text exists only in the
 * old name (before); INSERT text exists only in the new name (after). The old name is the
 * concatenation of EQUAL+DELETE chunks, the new name the concatenation of EQUAL+INSERT chunks.
 */
public record Chunk(ChunkOp op, String text) {}
