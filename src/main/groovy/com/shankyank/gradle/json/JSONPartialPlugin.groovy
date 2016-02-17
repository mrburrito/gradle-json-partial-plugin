package com.shankyank.gradle.json

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Plugin initialization.
 */
class JSONPartialPlugin implements Plugin<Project> {
    @Override
    void apply(final Project project) {
        project.logger.info("Applying JSONTemplatePlugin")
    }
}
