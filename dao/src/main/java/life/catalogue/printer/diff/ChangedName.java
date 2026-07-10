package life.catalogue.printer.diff;

import java.util.List;

/**
 * A name present on both sides but not identical: renamed, re-authored, etc.
 * @param similarity 0..100, 100 = identical (see StringSimilarity)
 */
public record ChangedName(String before, String after, List<Chunk> chunks, double similarity) {}
