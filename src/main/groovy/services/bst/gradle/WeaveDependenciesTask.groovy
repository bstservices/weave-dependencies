package services.bst.gradle

import groovy.xml.MarkupBuilder
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

@CacheableTask
class WeaveDependenciesTask extends DefaultTask
{
    /**
     * The classpath to the AspectJ compiler
     */
    @InputFiles
    @CompileClasspath
    public Configuration ajc

    /**
     * The classpath containing all of the aspects to weave
     */
    @InputFiles
    @Classpath
    @CompileClasspath
    public Configuration aspects

    /**
     * The files that need aspects woven into them
     */
    @InputFiles
    public Configuration dependenciesToWeave

    /**
     * The classpath for all transitive dependencies of the files that need
     * aspects woven
     */
    @InputFiles
    @Classpath
    @CompileClasspath
    public Configuration transitiveDependencyClasspath

    /**
     * A list of regular expressions for fully qualified class names to exclude
     * from weaving
     *
     * This is for aspects that are badly behaved. The format is that of
     * //aspectj/weave/exclude in AspectJ compiler aop.xml files.
     */
    public ArrayList<String> classesToExclude = new ArrayList<String>()

    /**
     * The xlint value for ajc
     */
    public String ajcXlint = "ignore"

    @OutputDirectory
    final String outDir = "${project.buildDir}/weaved-libs/${name}"

    @TaskAction
    def weave() {
        // Don't do anything if there's nothing to weave.
        if (dependenciesToWeave.isEmpty()) {
            return
        }

        // Clear the output directory.
        new File(outDir).traverse {
            it.delete()
        }

        // Extract aop.xml files
        def dir = temporaryDir
        dir.createNewFile()

        // Accumulate AOP files.
        def aopFileNames = []
        def aspectArtifacts = []
        aspects.resolvedConfiguration.getResolvedArtifacts().each {
            def file = it.file
            // We use the artifact ID to disambiguate artifacts with the same
            // name and different groups.
            def name = "${escapeArtifactName(it.id.displayName)}.jar"
            if (file.exists() && file.name.endsWith(".jar")) {
                def dest = "$dir/$name-dir"
                new File(dest).mkdir()
                ant.unzip(
                    src: file,
                    dest: dest,
                ) {
                    patternset {
                        include name: 'META-INF/aop.xml'
                    }
                }
                def jarAopFileName = new File("$dest/META-INF/aop.xml")
                if (jarAopFileName.exists()) {
                    aspectArtifacts << it
                    aopFileNames << jarAopFileName
                }
            }
        }

        // Add the extra excluded classes.
        def exclusionAopFileName = "$dir/aop.xml"
        def writer = new FileWriter(new File(exclusionAopFileName))
        try {
            def xml = new MarkupBuilder(writer)
            writer.write('<!DOCTYPE aspectj PUBLIC "-//AspectJ//DTD 1.5.0//EN" "http://www.eclipse.org/aspectj/dtd/aspectj_1_5_0.dtd">')
            xml.aspectj {
                aspects {}
                weaver {
                    classesToExclude.each { String classToExclude ->
                        aspect(name: classToExclude) {}
                    }
                }
            }
        } finally {
            writer.close()
        }
        aopFileNames << new File(exclusionAopFileName)

        // We need to filter the compile dependencies for AJC, because it
        // doesn't like if any of them don't exist.
        def existingCpJars =
            transitiveDependencyClasspath.filter { it.exists() }
        def existingCpJarsPath = existingCpJars.asPath

        // Instrument the JARs that need it. Those are in the aspectCompile
        // configuration, as well as the jars containing the aspects.
        def dependencyArtifacts =
            dependenciesToWeave.resolvedConfiguration.getResolvedArtifacts()
        ant.taskdef( resource:"org/aspectj/tools/ant/taskdefs/aspectjTaskdefs.properties", classpath: ajc.asPath)
        (dependencyArtifacts + aspectArtifacts).each {
            if (it.file.exists() && it.file.name.endsWith('.jar')) {
                def uniqueName =
                    "${escapeArtifactName(it.id.displayName)}.jar"
                ant.iajc(
                    fork: false,
                    aspectPath: existingCpJarsPath,
                    classpath: existingCpJarsPath,
                    xlint: ajcXlint,
                    inpath: it.file,
                    outjar: "$outDir/$uniqueName",
                ) {
                    inxml {
                        path {
                            aopFileNames.each {
                                pathelement(location: it.getAbsolutePath())
                            }
                        }
                    }
                }
            }
        }
    }

    static String escapeArtifactName(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_")
    }
}
