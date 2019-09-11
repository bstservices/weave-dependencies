package services.bst.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencyResolutionListener
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.tasks.SourceSet

import java.util.Set

class WeaveDependenciesPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        WeaveDependenciesExtension extension = project.getExtensions()
            .create("weaveDependencies", WeaveDependenciesExtension.class)

        // Add all the configurations.
        def ajc = project.configurations.create('ajc')
        def aspects = project.configurations.create('aspects')
        def weave = project.configurations.create('weave')
        project.configurations.getByName('compile').extendsFrom(
            weave,
            aspects,
        )
        ajc.dependencies.add(
            project.dependencies.add(
                'ajc',
                "org.aspectj:aspectjtools:${extension.aspectJVersion}",
            )
        )
        aspects.dependencies.add(
            project.dependencies.add(
                'aspects',
                "org.aspectj:aspectjrt:${extension.aspectJVersion}",
            )
        )

        // Add the weaving task.
        def task = project.tasks.register('weaveDependencies', WeaveDependenciesTask) { task ->
            def intransitiveJarsToWeave = weave.copyRecursive()
            intransitiveJarsToWeave.transitive = false

            def combinedClasspath = aspects.copyRecursive().extendsFrom(weave)

            task.aspects = aspects
            task.dependenciesToWeave = intransitiveJarsToWeave
            task.transitiveDependencyClasspath = combinedClasspath
            task.ajc = ajc
            task.classesToExclude = extension.classesToExclude
            task.ajcXlint = extension.ajcXlint
            task.ajcXlintFile = extension.ajcXlintFile
        }

        project.afterEvaluate {
            def preweaveDeps = getDirectPaths(weave)

            project.sourceSets.each { SourceSet sourceSet ->
                sourceSet.runtimeClasspath = sourceSet.runtimeClasspath.filter {
                    !preweaveDeps.contains(it.absolutePath)
                }
            }
        }

        // Insert the woven JARs as runtime dependencies after all other
        // dependencies have been added.
        project.getGradle().addListener(new DependencyResolutionListener() {
            private def weavedDepsAdded = false
            @Override
            void beforeResolve(ResolvableDependencies resolvableDependencies) {
                if (!weavedDepsAdded) {
                    def runtimeOnly = project.getConfigurations().getByName("runtimeOnly").getDependencies()
                    def weaveOutputDir = project.tasks.findByName('weaveDependencies').outputs.files.singleFile

                    runtimeOnly.add(project.getDependencies().add(
                        'runtimeOnly',
                        project.fileTree(weaveOutputDir) {
                            include '*.jar'
                            builtBy task
                        }
                    ))
                    weavedDepsAdded = true
                }
            }

            @Override
            void afterResolve(ResolvableDependencies resolvableDependencies) {}
        })
    }

    private Set<String> getDirectPaths(Configuration config) {
        Set<String> depSet = [] as Set
        config.resolvedConfiguration.firstLevelModuleDependencies.each { ResolvedDependency topDependency ->
            topDependency.moduleArtifacts.each { ResolvedArtifact artifact ->
                depSet.add(artifact.file.absolutePath)
            }
        }
        return depSet
    }
}
