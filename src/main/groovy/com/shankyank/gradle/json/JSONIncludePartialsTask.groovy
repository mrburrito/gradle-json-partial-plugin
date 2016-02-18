package com.shankyank.gradle.json

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectWriter
import groovy.io.FileType
import java.util.regex.Pattern
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

/**
 * Reads a JSON file, loading all partials as JSON and including their
 * values in the object tree. Once the tree is complete, it is serialized
 * out to the target file.
 */
class JSONIncludePartialsTask extends DefaultTask {
    /** The directory containing the source templates. */
    File sourceDir

    /** The directory containing the output files. */
    File targetDir

    /** The pattern used to identify included files. Default: ~/(?i).*\.json$/ */
    Pattern includePattern = ~/(?i).*\.json$/

    /** The directory where partials can be found. */
    File partialRoot

    /** True to pretty-print the output file. Default: true*/
    boolean prettyPrint = true

    JSONIncludePartialsTask() {
        group = 'JSON'
        description = 'Builds a JSON tree from a source file and its referenced partials.'
    }

    @TaskAction
    void buildJSONTree() {
        inputOutputMap.each { source, target ->
            logger.info("Generating ${target} from template ${source}")
            target.parentFile.mkdirs()
            JSONPartial template = new JSONPartial(partialRoot: partialRoot, partialFile: source)
            ObjectMapper mapper = new ObjectMapper()
            ObjectWriter writer = prettyPrint ? mapper.writerWithDefaultPrettyPrinter() : mapper.writer()
            writer.writeValue(target, template.resolvedValue)
        }
    }

    protected Map<File, File> getInputOutputMap() {
        Map ioMap = [:]
        logger.info("Searching for templates in ${sourceDir}")
        sourceDir.eachFileRecurse(FileType.FILES) { file ->
            logger.debug("Found file ${file}")
            if (file.name ==~ includePattern) {
                logger.debug("${file} is a template")
                ioMap[file] = getOutputFileForInputFile(file)
            }
        }
        logger.info("Found ${ioMap.size()} templates:\n${ioMap.collect { "${it}" }.join('\n')}")
        ioMap
    }

    protected File getOutputFileForInputFile(final File input) {
        List pathComponents = [ input.name ]
        File dir = input.parentFile
        while (dir != sourceDir) {
            pathComponents << dir.name
            dir = dir.parentFile
            if (!dir) {
                throw new IllegalStateException("${input} is not found below ${sourceDir}")
            }
        }
        new File(targetDir, pathComponents.reverse().join(File.separator))
    }
}
