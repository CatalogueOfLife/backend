package life.catalogue.printer.diff;

import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * A labelled, re-openable source of name labels, one per line.
 * For merge-based engines the stream MUST be sorted by DiffOptions.order.
 * The engine closes each Stream it obtains (try-with-resources), which releases any underlying
 * file handle or DB cursor/session via the stream's onClose.
 */
public record DiffInput(String label, Supplier<Stream<String>> lines) {}
