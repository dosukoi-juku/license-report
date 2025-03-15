package com.dosukoi.license.plugin

import groovy.util.XmlSlurper
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import nl.adaptivity.xmlutil.dom.get
import nl.adaptivity.xmlutil.dom.getChildNodes
import nl.adaptivity.xmlutil.dom.iterator
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlChildrenName
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class LicensesTask : DefaultTask() {

    @get:InputFile
    abstract val dependenciesJson: RegularFileProperty

    @get:OutputDirectory
    abstract var rawResourceDir: File

    @get:OutputFile
    abstract var licenses: File

    @get:OutputFile
    abstract var licensesMetadata: File

    val licensesMap: Map<String, String> = mapOf()
    val licenseOffsets: Map<String, String> = mapOf()

    val start = 0

    @TaskAction
    fun action() {
        initOutputDir()
        initLicenseFile()
        initLicensesMetadata()

        val dependenciesJsonFile = dependenciesJson.asFile.get()
        val artifactInfoSet = loadDependenciesJson(dependenciesJsonFile)

        if (DependencyUtil.ABSENT_ARTIFACT in artifactInfoSet) {
            if (artifactInfoSet.size > 1) {
                throw IllegalStateException("artifactInfoSet that contains EMPTY_ARTIFACT should not contain other artifacts.")
            }
            addDebugLicense()
        } else {
            artifactInfoSet.forEach {
                addLicensesFromPom(it)
            }
        }
    }

    private fun addDebugLicense() {
        appendDependency(
            ABSENT_DEPENDENCY_KEY,
            ABSENT_DEPENDENCY_TEXT.toByteArray(Charsets.UTF_8)
        )
    }

    fun initOutputDir(rawResourceDir: File): File {
        return if (!rawResourceDir.exists()) {
            rawResourceDir.apply { mkdirs() }
        } else {
            rawResourceDir
        }
    }

    fun initLicenseFile(licenses: File): File {
        licenses.writer().buffered().use {
            it.write("")
        }
        return licenses
    }

    fun initLicensesMetadata(licensesMetadata: File): File {
        licensesMetadata.writer().buffered().use {
            it.write("")
        }
        return licensesMetadata
    }

    fun addLicensesFromPom(artifact: Artifact) {
        val pomFile = DependencyUtil.resolvePomFileArtifact(project, artifact)

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

        val licenceKey = "${group}:${name}"
        if (pom.licenses.size == 1) {
            appendDependency(
                Dependency(
                    key = licenceKey,
                    name = pom.licenses[0].name
                ),
                pom.licenses[0].url.toByteArray(Charsets.UTF_8)
            )
        } else {
            pom.licenses.forEach {
                appendDependency(
                    dependency = Dependency(
                        key = "$licenceKey ${it.name}",
                        name = pom.name
                    ),
                    license = it.url.toByteArray(Charsets.UTF_8)
                )
            }
        }
    }

    fun appendDependency(key: String, license: ByteArray) {
        appendDependency(Dependency(key, key), license)
    }

    fun appendDependency(dependency: Dependency, license: ByteArray) {
        val licenseText = String(license, Charsets.UTF_8)
        if (licensesMap.containsKey(licenseText)) {
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
        licenses.appendBytes(content)
        start += content.size
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

        fun isGranularVersion(version: String): Boolean {
            val versions = version.split("\\.".toRegex())
            return versions.isNotEmpty() && Integer.valueOf(versions[0]) == GRANULAR_BASE_VERSION
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

        fun loadDependenciesJson(jsonFile: File): Set<Artifact> {
            val json = jsonFile.readText(Charsets.UTF_8)
            val artifactInfoSet = Json.decodeFromString<Set<Artifact>>(json)
            return artifactInfoSet
        }
    }
}

@Serializable
data class Pom(
    @XmlElement(true)
    val groupId: String,
    @XmlElement(true)
    val version: String,
    @XmlElement(true)
    val name: String,
    @XmlChildrenName("license")
    val licenses: List<License>,
)

@Serializable
@XmlSerialName("license", "", "")
@SerialName("license")
data class License(
    @XmlElement(true)
    val name: String,
    @XmlElement(true)
    val url: String,
)