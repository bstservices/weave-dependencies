package services.bst.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencyResolutionListener
import org.gradle.api.artifacts.ResolvableDependencies

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
            aspects,
            weave,
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
        }

        // Insert the woven JARs as runtime dependencies after all other
        // dependencies have been added.
        project.getGradle().addListener(new DependencyResolutionListener() {
            @Override
            void beforeResolve(ResolvableDependencies resolvableDependencies) {
                def runtimeDeps = project.getConfigurations().getByName("runtimeOnly").getDependencies()
                def weaveOutputDir = project.tasks.findByName('weaveDependencies').outputs.files.singleFile
                runtimeDeps.add(project.getDependencies().add(
                    'runtimeOnly',
                    project.fileTree(weaveOutputDir) {
                        include '*.jar'
                        builtBy task
                    }
                ))
                project.getGradle().removeListener(this)
            }

            @Override
            void afterResolve(ResolvableDependencies resolvableDependencies) {}
        })
    }
}
