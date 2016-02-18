package com.shankyank.gradle.json

import com.fasterxml.jackson.databind.ObjectMapper
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

/**
 * Reads a JSON file, loading all partials as JSON and including their
 * values in the object tree. Once the tree is complete, it is serialized
 * out to the target file.
 */
class JSONIncludePartialsTask extends DefaultTask {
    /** The source file. */
    File source

    /** The target file. */
    File target

    /** The directory where partials can be found. */
    File partialRoot

    /** True to pretty-print the output file. */
    boolean prettyPrint = true

    JSONIncludePartialsTask() {
        group = 'JSON Template'
        description = 'Builds a JSON tree from a source file and its referenced partials.'
    }

    @TaskAction
    void buildJSONTree() {
        target.parentFile.mkdirs()
        JSONPartial template = new JSONPartial(partialRoot: partialRoot, partialFile: source)
        ObjectMapper mapper = new ObjectMapper()
        mapper.writerWithDefaultPrettyPrinter().writeValue(target, template.resolvedValue)
    }
}
