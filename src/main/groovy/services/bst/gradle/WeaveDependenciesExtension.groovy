package services.bst.gradle

class WeaveDependenciesExtension {
    /**
     * The version of AspectJ to use.
     */
    String aspectJVersion = '1.9.2'
    /**
     * A list of classes to exclude from being woven.
     *
     * This is to handle badly behaved aspects. The actual format is defined
     * by AspectJ's aop.xml schema for weaver excludes.
     */
    String[] classesToExclude = []
    /**
     * The xlint value for Ajc
     */
    String ajcXlint = "ignore"

    String getAspectJVersion() {
        return aspectJVersion
    }

    String[] getClassesToExclude() {
        return classesToExclude
    }

    String getAjcXlint() {
        return ajcXlint
    }
}
