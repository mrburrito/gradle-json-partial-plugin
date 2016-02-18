package com.shankyank.gradle.json

import com.fasterxml.jackson.databind.ObjectMapper
import groovy.json.JsonSlurper
import groovy.transform.Canonical
import groovy.transform.Memoized
import groovy.util.logging.Slf4j

/**
 * A Partial definition object.
 */
@Canonical
@Slf4j
class JSONPartial {
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
    /** The JSON mapper */
    private static final ObjectMapper JSON = new ObjectMapper()

    /** The cache of previously loaded partials. */
    private static final Map<String, Map> CACHE = [:]

    /**
     * Read a JSON file, returning the contents as a Map.
     * @param file the file to read
     * @return a Map containing the values
     */
    private static Map parseJSONFile(final File file) {
        JSON.readValue(file, LinkedHashMap)
    }

    /**
     * Returns a sorted map where keys are ordered the same as the keys in the
     * input map. If the input map is not sorted, output may not be consistent.
     * Keys found in the target but not the source will be sorted naturally
     * after all keys in the source map.
     * @param source the source Map whose key order will be copied
     * @param target the Map whose keys will be re-ordered
     * @return a SortedMap whose key order matches the order of keys in the input map
     */
    private static Map matchKeyOrderingFromSourceMap(final Map source, final Map target) {
        Map sortMap = [:]
        source.keySet().eachWithIndex{ key, idx -> sortMap[key] = idx }
        ([] + target.keySet()).with {
            removeAll(source.keySet())
            sort().eachWithIndex { key, idx -> sortMap[key] = source.size() + idx }
        }
        target.sort { e1, e2 -> sortMap[e1.key] <=> sortMap[e2.key] }
    }

    /** The root path for resolving partials. */
    File partialRoot

    /** The path to the Partial definition. */
    File partialFile

    /** The path to the desired object from the partial; empty string indicates the root. */
    String path = ''

    /**
     * @return the value of this partial at the configured path
     */
    @Memoized
    Map getResolvedValue() {
        Map value = fullPartial
        pathComponents.each { value = value."${it}" as Map }
        value
    }

    @Override
    String toString() {
        "Partial: ${partialFile.absolutePath}${path ? "::${path}" : ''}"
    }

    /**
     * Executes a depth-first search and replace through the provided JSON tree,
     * replacing all partial definitions with the resolved objects and merging
     * other properties of the containing object with those values.
     * @param root the root of the tree to process
     * @return a copy of the provided Map, with all includes replaced
     */
    private Map replacePartialsInMap(final Map root) {
        log.debug("Replacing partials in: ${root}")
        // maintain ordering
        Map keyOrder = [:]
        root.keySet().eachWithIndex { key, idx -> keyOrder[key] = idx }
        Map replaced = parseIncludes(root[INCLUDE_PARTIAL]).collectEntries { it.resolvedValue }
        replaced << root.collectEntries { key, value ->
            // we've already parsed the partials above; ignore them here
            key != INCLUDE_PARTIAL ? [ (key): generateReplacementValue(value) ] : [:]
        }
        replaced = matchKeyOrderingFromSourceMap(root, replaced)
        log.debug("Replaced Partials: ${replaced}")
        replaced
    }

    /**
     * Generates a replacement value for the provided value.
     * @param value the value to replace
     * @return the replaced value
     */
    protected Object generateReplacementValue(final Object value) {
        if (value instanceof Map) {
            replacePartialsInMap(value)
        } else if (value instanceof Collection) {
            value.collect { generateReplacementValue(it) }
        } else {
            value
        }
    }

    /**
     * Parses the value of an INCLUDE_PARTIAL, returning the List
     * of Partials configured.
     * @param definition the include definition
     * @return the List of Partials constructed from the provided definition
     */
    private List<JSONPartial> parseIncludes(final Object definition) {
        def myLog = log
        definition?.with {
            myLog.info("Parsing includes from definition: ${definition}")
            [ it ].flatten().findAll().collect { parsePartialDefinition(it) }.with {
                myLog.info("Found includes: ${it}")
                it
            }
        } ?: []
    }

    /**
     * Evaluates a partial definition, creating a Partial based on whether
     * it is a String definition or a Map definition.
     * @param definition the partial definition
     * @return the parsed Partial
     */
    private JSONPartial parsePartialDefinition(final Object definition) {
        log.debug("Creating Partial for definition: ${definition}")
        String partialPath
        String propertyPath = ''
        switch (definition) {
            case CharSequence:
                log.debug("${definition} is CharSequence")
                partialPath = definition as String
                break
            case Map:
                log.debug("${definition} is Map")
                partialPath = definition.get(PARTIAL_KEY) as String
                propertyPath = definition.get(PARTIAL_PATH_KEY)?.trim() ?: ''
                break
            default:
                throw new IllegalArgumentException("Invalid Partial configuration: ${definition}")
        }
        new JSONPartial(partialRoot: partialRoot, partialFile: getAbsolutePartialFile(partialPath), path: propertyPath).with {
            log.debug("${definition} => ${it}")
            it
        }
    }

    /**
     * Construct the full path to a partial file based on a relative path
     * from the partial root.
     * @param path the relative path to the partial
     * @return a File pointing to the requested partial
     */
    private File getAbsolutePartialFile(final String path) {
        log.debug("Generating partial file for relative path: ${path}")
        if (!path?.trim()) {
            throw new IllegalArgumentException("Relative path to partial must be provided.")
        }
        new File(partialRoot, path).with {
            log.debug("Generated partial file: ${it.absolutePath}")
            it
        }
    }

    /**
     * @return the path components
     */
    @Memoized
    private List getPathComponents() {
        path?.split(/\./)?.collect { it?.trim() }?.findAll() ?: []
    }

    /**
     * @return the cache key for this partial
     */
    @Memoized
    private String getCacheKey() {
        partialFile.absolutePath
    }

    /**
     * Attempts to return the resolved partial from cache, loading it
     * from the provided file and recursively resolving any partials
     * found if the loaded object is a Map.
     * @param partial the partial
     * @return the loaded and resolved partial
     */
    private Map getFullPartial() {
        Map resolvedPartial = CACHE[cacheKey]
        if (resolvedPartial == PROCESSING_PARTIAL_MARKER) {
            throw new IllegalStateException("Circular reference to partial file ${partialFile.absolutePath}!")
        } else if (resolvedPartial == null) {
            CACHE[cacheKey] = PROCESSING_PARTIAL_MARKER
            resolvedPartial = readPartialFromFile(partialFile)
            CACHE[cacheKey] = resolvedPartial
        }
        resolvedPartial
    }

    /**
     * Loads the partial from a file, resolving nested partials when the root of
     * the partial is a Map.
     * @param file the partial file
     * @return the resolved partial
     */
    private Map readPartialFromFile(final File file) {
        if (!file.file) {
            throw new FileNotFoundException("Partial ${file} does not exist.")
        }
        replacePartialsInMap(parseJSONFile(file))
    }
}
