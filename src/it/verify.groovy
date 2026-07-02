import java.util.jar.JarFile

def artifactDirectory = new File(
        localRepositoryPath,
        "io/github/alaptseu/json-field-presence/${artifactVersion}")
def binaryJar = new File(
        artifactDirectory,
        "json-field-presence-${artifactVersion}.jar")
def sourcesJar = new File(
        artifactDirectory,
        "json-field-presence-${artifactVersion}-sources.jar")
def javadocJar = new File(
        artifactDirectory,
        "json-field-presence-${artifactVersion}-javadoc.jar")
def pomFile = new File(
        artifactDirectory,
        "json-field-presence-${artifactVersion}.pom")
def sbomFile = new File(
        artifactDirectory,
        "json-field-presence-${artifactVersion}-cyclonedx.json")

[binaryJar, sourcesJar, javadocJar, pomFile, sbomFile].each { artifact ->
    assert artifact.isFile(): "Missing staged artifact: ${artifact}"
}

def sbom = new groovy.json.JsonSlurper().parse(sbomFile)
assert sbom.bomFormat == "CycloneDX"
assert sbom.specVersion == "1.6"
assert sbom.serialNumber == null: "Reproducible SBOM must not have a random serial number"
assert sbom.components.any { component ->
    component.group == "com.fasterxml.jackson.core" &&
            component.name == "jackson-databind"
}: "SBOM does not describe the transitive runtime dependency"

def binary = new JarFile(binaryJar)
try {
    assert binary.manifest.mainAttributes.getValue("Automatic-Module-Name") ==
            "io.github.alaptseu.jsonpresence"
    def license = binary.getJarEntry("META-INF/LICENSE")
    assert license != null: "Runtime JAR does not contain META-INF/LICENSE"
    assert binary.getInputStream(license).getText("UTF-8").startsWith("MIT License")
} finally {
    binary.close()
}

def sources = new JarFile(sourcesJar)
try {
    assert sources.getJarEntry(
            "io/github/alaptseu/jsonpresence/JsonFieldPresence.java") != null
    assert sources.getJarEntry("META-INF/LICENSE") != null
} finally {
    sources.close()
}

def javadocs = new JarFile(javadocJar)
try {
    assert javadocs.entries().find { entry ->
        entry.name.endsWith("/JsonFieldPresence.html")
    } != null
} finally {
    javadocs.close()
}

def pomText = pomFile.getText("UTF-8")
assert pomText.contains("<artifactId>jackson-databind</artifactId>")
def jacksonDirectory = new File(
        localRepositoryPath,
        "com/fasterxml/jackson/core/jackson-databind")
assert jacksonDirectory.isDirectory(): "Transitive Jackson artifact was not staged"
assert jacksonDirectory.listFiles().any { versionDirectory ->
    versionDirectory.listFiles().any { file ->
        file.name.startsWith("jackson-databind-") && file.name.endsWith(".jar")
    }
}

def expectedClasses = basedir.name == "jpms-consumer"
        ? ["target/classes/module-info.class", "target/classes/consumer/jpms/ModuleConsumer.class"]
        : ["target/classes/consumer/classpath/ClasspathConsumer.class"]
expectedClasses.each { relativePath ->
    assert new File(basedir, relativePath).isFile():
            "Consumer did not compile expected class: ${relativePath}"
}

def jacksonArtifacts = ["jackson-annotations", "jackson-core", "jackson-databind"]
        .collect { artifactId ->
            def artifactRoot = new File(
                    localRepositoryPath,
                    "com/fasterxml/jackson/core/${artifactId}")
            def jars = []
            artifactRoot.eachFileRecurse { file ->
                if (file.isFile()
                        && file.name.startsWith("${artifactId}-")
                        && file.name.endsWith(".jar")) {
                    jars.add(file)
                }
            }
            assert jars.size() == 1:
                    "Expected one staged runtime JAR for ${artifactId}, found ${jars}"
            jars.first()
        }

def javaExecutable = new File(
        new File(System.getProperty("java.home"), "bin"),
        System.getProperty("os.name").toLowerCase().contains("windows")
                ? "java.exe"
                : "java")
def runtimeEntries = [new File(basedir, "target/classes"), binaryJar] + jacksonArtifacts
def runtimePath = runtimeEntries.collect { it.absolutePath }
        .join(File.pathSeparator)
def command
def successMarker
if (basedir.name == "jpms-consumer") {
    command = [
            javaExecutable.absolutePath,
            "--module-path",
            runtimePath,
            "-m",
            "consumer.jpms/consumer.jpms.ModuleConsumer"]
    successMarker = "jpms-consumer-ok"
} else {
    command = [
            javaExecutable.absolutePath,
            "-cp",
            runtimePath,
            "consumer.classpath.ClasspathConsumer"]
    successMarker = "classpath-consumer-ok"
}

def process = new ProcessBuilder(command)
        .redirectErrorStream(true)
        .start()
def processOutput = process.inputStream.getText("UTF-8")
def exitCode = process.waitFor()
assert exitCode == 0:
        "Consumer process failed with exit ${exitCode}: ${processOutput}"
assert processOutput.contains(successMarker):
        "Consumer process did not emit ${successMarker}: ${processOutput}"

return true
