package io.github.dosukoi_juku.license.report

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipFile
import kotlin.collections.forEach
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlChildrenName
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class LicensesTask : DefaultTask() {

    @get:InputFile
    abstract val dependenciesJson: RegularFileProperty

    @get:OutputDirectory
    abstract val rawResourceDir: DirectoryProperty

    @get:OutputFile
    abstract val licenses: RegularFileProperty

    @get:OutputFile
    abstract val licensesMetadata: RegularFileProperty

    @get:Internal
    val licensesMap = mutableMapOf<String, String>()

    @get:Internal
    val licenseOffsets = mutableMapOf<String, String>()

    @get:Internal
    val googleServiceLicenses = mutableSetOf<String>()

    @get:Internal
    var start = 0

    @TaskAction
    fun action() {
        initOutputDir()
        initLicenseFile()
        initLicensesMetadata()

        val dependenciesJsonFile = dependenciesJson.asFile.get()
        val artifactInfoSet = loadDependenciesJson(dependenciesJsonFile)

        if (DependencyUtil.ABSENT_Dependency in artifactInfoSet) {
            if (artifactInfoSet.size > 1) {
                throw IllegalStateException("artifactInfoSet that contains EMPTY_ARTIFACT should not contain other artifacts.")
            }
            addDebugLicense()
        } else {
            artifactInfoSet.forEach {
                println("artifact: ${it.group}:${it.name}:${it.version}")
                if (isGoogleServices(it.group)) {
                    println("isGoogleServices: ${it.group}:${it.name}")
                    if (!it.name.endsWith(LICENSE_ARTIFACT_SUFFIX)) {
                        println("not ends with $LICENSE_ARTIFACT_SUFFIX")
                        addLicensesFromPom(it)
                    }

                    if (isGranularVersion(it.version) || it.name.endsWith(LICENSE_ARTIFACT_SUFFIX)) {
                        println("isGranularVersion: ${it.version} or ends with $LICENSE_ARTIFACT_SUFFIX")
                        addGooglePlayServiceLicenses(it)
                    }
                } else {
                    addLicensesFromPom(it)
                }
            }
        }

        writeMetadata()
    }

    private fun addDebugLicense() {
        appendDependency(
            ABSENT_DEPENDENCY_KEY,
            ABSENT_DEPENDENCY_TEXT.toByteArray(Charsets.UTF_8)
        )
    }

    fun initOutputDir() {
        if (!rawResourceDir.asFile.get().exists()) {
            rawResourceDir.asFile.get().mkdirs()
        }
    }

    fun initLicenseFile() {
        licenses.asFile.get().writer().buffered().use {
            it.write("")
        }
    }

    fun initLicensesMetadata() {
        licensesMetadata.asFile.get().writer().buffered().use {
            it.write("")
        }
    }

    fun addLicensesFromPom(dependency: io.github.dosukoi_juku.license.report.Dependency) {
        val pomFile = DependencyUtil.resolvePomFileArtifact(project, dependency)
        addLicensesFromPom(pomFile, dependency.group, dependency.name)
    }

    fun addLicensesFromPom(pomFile: File?, group: String, name: String) {
        if (pomFile == null || !pomFile.exists()) {
            logger.error("POM file $pomFile for $group:$name does not exist")
            return
        }

        val fileText = pomFile.readText(Charsets.UTF_8)
        val xml = XML {
            defaultPolicy {
                ignoreUnknownChildren()
            }
        }
        val serializer = serializer<Pom>()
        val pom = xml.decodeFromString(serializer, fileText)

        if (pom.licenses.isEmpty()) {
            logger.error("No licenses found in POM file $pomFile for $group:$name")
            return
        }

        var libraryName = pom.name
        val licenceKey = "${group}:${name}"
        if (libraryName == null || libraryName.isBlank()) {
            libraryName = licenceKey
        }
        if (pom.licenses.size == 1) {
            appendDependency(
                Dependency(
                    key = licenceKey,
                    name = libraryName
                ),
                pom.licenses[0].url.toByteArray(Charsets.UTF_8)
            )
        } else {
            pom.licenses.forEach { license ->
                val licenseName = license.name
                val url = license.url
                appendDependency(
                    dependency = Dependency(
                        key = "$licenceKey $licenseName",
                        name = libraryName
                    ),
                    license = url.toByteArray(Charsets.UTF_8)
                )
            }
        }
    }

    fun appendDependency(key: String, license: ByteArray) {
        appendDependency(Dependency(key, key), license)
    }

    fun appendDependency(dependency: Dependency, license: ByteArray) {
        val licenseText = String(license, Charsets.UTF_8)
        if (licensesMap.containsKey(dependency.key)) {
            return
        }

        val offsets = if (licenseOffsets.containsKey(licenseText)) {
            licenseOffsets[licenseText]!!
        } else {
            "$start:${license.size}"
        }

        if (!licenseOffsets.containsKey(licenseText)) {
            licenseOffsets.put(licenseText, offsets)
            appendLicenseContent(license)
            appendLicenseContent(LINE_SEPARATOR)
        }
        licensesMap.put(dependency.key, dependency.buildLicenseMetadata(offsets))
    }

    fun appendLicenseContent(content: ByteArray) {
        licenses.asFile.get().appendBytes(content)
        start += content.size
    }

    fun writeMetadata() {
        licensesMap.forEach {
            licensesMetadata.asFile.get().appendText(it.value, Charsets.UTF_8)
            licensesMetadata.asFile.get().appendBytes(LINE_SEPARATOR)
        }
    }

    fun addGooglePlayServiceLicenses(dependency: io.github.dosukoi_juku.license.report.Dependency) {
        println("addGooglePlayServiceLicenses: ${dependency.group}:${dependency.name}")
        val artifactFile = DependencyUtil.getLibraryFile(project, dependency)
        if (artifactFile == null || !artifactFile.exists()) {
            logger.warn("Unable to find Google Play Services Artifact for $dependency")
            return
        }
        addGooglePlayServicesLicenses(artifactFile)
    }

    fun addGooglePlayServicesLicenses(artifactFile: File) {
        val licensesZip = ZipFile(artifactFile)
        val jsonFile = licensesZip.getEntry("third_party_licenses.json")
        val txtFile = licensesZip.getEntry("third_party_licenses.txt")

        if (jsonFile == null || txtFile == null) {
            logger.error("No licenses found in Google Play Services artifact")
            return
        }

        val jsonText = licensesZip.getInputStream(jsonFile).buffered().use {
            it.readBytes()
        }.toString(Charsets.UTF_8)
        val entries = Json.decodeFromString<Map<String, Entry>>(jsonText)
        entries.forEach {  (key, value) ->
            if(!googleServiceLicenses.contains(key)) {
                licensesZip.getInputStream(txtFile).buffered().use {
                    val content = getBytesFromInputStream(it, value.start.toLong(), value.length)
                    googleServiceLicenses.add(key)
                    appendDependency(key, content)
                }
            }
        }

    }


//
//    fun addGooglePlayServiceLicense(artifactFile: File) {
//        val licensesZip = ZipFile(artifactFile)
//
//        val jsonFile = licensesZip.getEntry("third_party_licenses.json")
//        val txtFile = licensesZip.getEntry("third_party_licenses.txt")
//
//        if (jsonFile == null || txtFile == null) {
//            logger.error("No licenses found in Google Play Services artifact")
//            return
//        }
//
//
//    }

    data class Dependency(
        val key: String,
        val name: String
    ) {
        fun buildLicenseMetadata(offset: String): String {
            return "$offset $name"
        }

        override fun toString(): String {
            return "$key -> $name"
        }
    }

    companion object {
        private const val GRANULAR_BASE_VERSION = 14
        private const val FAIL_READING_LICENSES_ERROR = "Failed to read license text."
        private val LINE_SEPARATOR = System.lineSeparator().toByteArray(Charsets.UTF_8)

        private const val ABSENT_DEPENDENCY_KEY = "Debug License Info"
        private const val ABSENT_DEPENDENCY_TEXT = ("Licenses are " +
                "only provided in build variants " +
                "(e.g. release) where the Android Gradle Plugin " +
                "generates an app dependency list.")

        private const val GOOGLE_PLAY_SERVICES_GROUP = "com.google.android.gms"
        private const val FIREBASE_GROUP = "com.google.firebase"

        private const val LICENSE_ARTIFACT_SUFFIX = "-license"

        fun isGranularVersion(version: String): Boolean {
            val versions = version.split("\\.".toRegex())
            println("isGranularVersion: ${versions[0]}.${versions[1]}.${versions[2]}")
            return versions.isNotEmpty() && Integer.valueOf(versions[0]) >= GRANULAR_BASE_VERSION
        }

        fun getBytesFromInputStream(
            inputStream: InputStream,
            offset: Long,
            length: Int,
        ): ByteArray {
            try {
                val buffer = ByteArray(1024)
                val textArray = ByteArrayOutputStream()

                inputStream.skip(offset)
                var bytesRemaining = if (length > 0) length else Integer.MAX_VALUE
                var bytes = 0

                while (bytesRemaining > 0) {
                    bytes = inputStream.read(buffer, 0, bytesRemaining.coerceAtMost(buffer.size))
                    if (bytes < 0) {
                        break
                    }
                    textArray.write(buffer, 0, bytes)
                    bytesRemaining -= bytes
                }
                inputStream.close()
                return textArray.toByteArray()

            } catch (e: IOException) {
                throw RuntimeException(FAIL_READING_LICENSES_ERROR, e)
            }
        }

        fun loadDependenciesJson(jsonFile: File): Set<io.github.dosukoi_juku.license.report.Dependency> {
            val json = jsonFile.readText(Charsets.UTF_8)
            val dependencyInfoSet = Json.decodeFromString<Set<io.github.dosukoi_juku.license.report.Dependency>>(json)
            return dependencyInfoSet
        }

        fun isGoogleServices(group: String): Boolean {
            return GOOGLE_PLAY_SERVICES_GROUP.equals(group, ignoreCase = true) ||
                    FIREBASE_GROUP.equals(group, ignoreCase = true)
        }
    }
}

@Serializable
data class Pom(
    @XmlElement(true)
    val version: String,
    @XmlElement(true)
    val name: String,
    @XmlChildrenName("license")
    val licenses: List<PomLicense>,
)

@Serializable
@XmlSerialName("license", "", "")
@SerialName("license")
data class PomLicense(
    @XmlElement(true)
    val name: String,
    @XmlElement(true)
    val url: String,
)

@Serializable
data class Entry(
    val length: Int,
    val start: Int,
)