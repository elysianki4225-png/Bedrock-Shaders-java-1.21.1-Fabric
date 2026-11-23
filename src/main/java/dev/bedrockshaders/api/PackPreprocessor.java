package dev.bedrockshaders.api;

/**
 * Called for every archive before it is imported; may rewrite or veto the import.
 *
 * <p>The intended use is teaching the importer about a pack layout it does not recognise:
 * inspect {@link PackDescriptor#entries()}, then either adjust the family or queue extra
 * files. Preprocessors run in registration order and each sees the previous one's changes.</p>
 */
@FunctionalInterface
public interface PackPreprocessor {

    /**
     * @param descriptor the archive about to be imported; mutate it in place
     * @return the descriptor to continue with, or {@code null} to leave it unchanged
     */
    PackDescriptor process(PackDescriptor descriptor);
}
