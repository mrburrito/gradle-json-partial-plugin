package com.shankyank.gradle.json

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.json.StreamingJsonBuilder
import groovy.transform.Memoized
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction

/**
 * Reads a JSON file, loading all partials as JSON and including their
 * values in the object tree. Once the tree is complete, it is serialized
 * out to the target file.
 */
class JSONIncludePartialsTask extends DefaultTask {
    /** The include partial marker. */
    static final String INCLUDE_PARTIAL = '##include'
    /** The partial location key. */
    static final String PARTIAL_KEY = 'partial'
    /** The partial path key. */
    static final String PARTIAL_PATH_KEY = 'path'

    /** The UTF-8 Character Set */
    private static final String UTF8 = 'UTF-8'
    /** The marker object used to detect circular references in partials. */
    private static final Object PROCESSING_PARTIAL_MARKER = new Object()

    /**
     * Read a JSON file, returning the contents as a Map.
     * @param file the file to read
     * @return a Map containing the values
     */
    private static Object parseJSONFile(final File file) {
        new JsonSlurper().parse(file, UTF8)
    }

    /**
     * @return true if the provided value is a partial definition
     */
    private static boolean isPartial(final Object value) {
        value instanceof Map && value.containsKey(INCLUDE_PARTIAL) && value.size() == 1
    }

    /** The source file. */
    File source

    /** The target file. */
    File target

    /** The directory where partials can be found. */
    File partialRoot

    /** True to pretty-print the output file. */
    boolean prettyPrint = true

    /** The cache of resolved partials. */
    private final Map partialCache

    JSONIncludePartialsTask() {
        group = 'JSON Template'
        description = 'Builds a JSON tree from a source file and its referenced partials.'

        partialCache = [:]
    }

    @TaskAction
    void buildJSONTree() {
        Map root = parseJSONFile(source) as Map
        Map resolved = replacePartialsInMap(root)
        String json = JsonOutput.toJson(resolved)
        if (prettyPrint) {
            json = JsonOutput.prettyPrint(json)
        }
        target << json
    }

    /**
     * Executes a depth-first search of the provided JSON tree, replacing all Maps
     * containing a single key/value pair, ${INCLUDE_PARTIAL} ==> <partial_specification>,
     * with the recursively processed object graph of the target partial.
     * @param root the root of the tree to process
     * @return a copy of the provided Map, with all includes replaced
     */
    private Map replacePartialsInMap(final Map root) {
        root.collectEntries { key, value ->
            try {
                Object newValue = value
                if (isPartial(value)) {
                    newValue = resolvePartial(new Partial(value as Map))
                }
                [ (key): newValue ]
            } catch (Exception e) {
                throw new GradleException("Error replacing partials", e)
            }
        }
    }

    /**
     * Resolves a Partial as the value of the parsed partial file at
     * the indicated path. Paths may not use wildcarding and must be
     * simple property names separated by '.'s. (e.g. 'a.b.c').
     * @param partial the partial
     * @return the resolved portion of the partial at the configured path
     */
    private def resolvePartial(final Partial partial) {
        partial.extractValueFromPartial(getResolvedPartial(partial))
    }

    /**
     * Attempts to return the resolved partial from cache, loading it
     * from the provided file and recursively resolving any partials
     * found if the loaded object is a Map.
     * @param partial the partial
     * @return the loaded and resolved partial
     */
    private def getResolvedPartial(final Partial partial) {
        String cacheKey = partial.partialFile.absolutePath
        def resolvedPartial = partialCache[cacheKey]
        if (resolvedPartial == PROCESSING_PARTIAL_MARKER) {
            throw new IllegalStateException("Circular reference to partial file ${file.absolutePath}!")
        } else if (resolvedPartial == null) {
            partialCache[cacheKey] = PROCESSING_PARTIAL_MARKER
            resolvedPartial = readPartialFromFile(partial.partialFile)
            partialCache[cacheKey] = resolvedPartial
        }
        resolvedPartial
    }

    /**
     * Loads the partial from a file, resolving nested partials when the root of
     * the partial is a Map.
     * @param file the partial file
     * @return the resolved partial
     */
    private def readPartialFromFile(final File file) {
        if (!file.file) {
            throw new FileNotFoundException("Partial ${file} does not exist.")
        }
        def partial = parseJSONFile(file)
        if (partial instanceof Map) {
            partial = replacePartialsInMap(partial)
        }
        partial
    }

    /**
     * A Partial definition object.
     */
    private class Partial {
        /** The path to the Partial definition, relative to the partialRoot. */
        final String partial

        /** The path to the desired object from the partial; empty string indicates the root. */
        final String path

        Partial(final Map definition) {
            def include = definition[INCLUDE_PARTIAL]
            switch (include) {
                case CharSequence:
                    partial = include as String
                    path = ''
                    break
                case Map:
                    partial = include[PARTIAL_KEY]
                    path = include[PARTIAL_PATH_KEY]?.trim() ?: ''
                    break
                default:
                    throw new IllegalStateException("Invalid Partial configuration: ${definition}")
            }
        }

        /**
         * @return the file, resolved from the partialRoot directory and configured relative path,
         * containing this partial
         */
        @Memoized
        File getPartialFile() {
            new File(partialRoot, partial)
        }

        /**
         * Navigates the provided partial object, returning the value at the configured path.
         * @param object the resolved partial
         * @return the value found at the configured path
         */
        def extractValueFromPartial(final def object) {
            def value = object
            path?.split(/\./)?.collect { it?.trim() }?.findAll()?.each {
                value = value."${it}"
            }
            value
        }
    }
}
