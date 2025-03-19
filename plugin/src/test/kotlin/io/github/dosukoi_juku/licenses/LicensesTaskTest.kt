package io.github.dosukoi_juku.licenses

import io.github.dosukoi_juku.license.report.LicensesTask
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.api.Project
import org.gradle.internal.impldep.org.testng.Assert
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class LicensesTaskTest {

    @TempDir
    lateinit var tempFolder: File

    lateinit var project: Project

    lateinit var licensesTask: LicensesTask

    @BeforeEach
    fun setup() {
        val outputLicenses = File(tempFolder, "testLicences")
        val outputMetadata = File(tempFolder, "testMetadata")

        project = ProjectBuilder.builder().withProjectDir(File(BASE_DIR)).build()
        licensesTask = project.tasks.create("generateLicenses", LicensesTask::class.java)

        licensesTask.rawResourceDir = tempFolder
        licensesTask.licenses = outputLicenses
        licensesTask.licensesMetadata = outputMetadata
    }

    @Test
    fun testInitOutputDir() {
        licensesTask.initOutputDir()

        assertTrue(licensesTask.rawResourceDir.exists())
    }

    @Test
    fun testInitLicenseFile() {
        licensesTask.initLicenseFile()

        assertTrue(licensesTask.licenses.exists())
        assertEquals(0, Files.size(licensesTask.licenses.toPath()))
    }

    @Test
    @Throws(IOException::class)
    fun testInitLicensesMetadata() {
        licensesTask.initLicensesMetadata()

        assertTrue(licensesTask.licensesMetadata.exists())
        assertEquals(0, Files.size(licensesTask.licensesMetadata.toPath()))
    }

    @Test
    fun testIsGranularVersion_True() {
        val versionTrue = "14.6.0"
        assertTrue(LicensesTask.isGranularVersion(versionTrue))
    }

    @Test
    fun testIsGranularVersion_False() {
        val versionFalse = "11.4.0"
        assertFalse(LicensesTask.isGranularVersion(versionFalse))
    }

    @Test
    @Throws(IOException::class)
    fun testAddLicensesFromPom() {
        val deps1 = getResourceFile("dependencies/groupA/deps1.pom")
        val name1 = "deps1"
        val group1 = "groupA"
        licensesTask.addLicensesFromPom(deps1, group1, name1)

        val content = String(Files.readAllBytes(licensesTask.licenses.toPath()), UTF_8)
        val expected = "http://www.opensource.org/licenses/mit-license.php" + LINE_BREAK
        assertTrue(licensesTask.licensesMap.containsKey("groupA:deps1"))
        assertEquals(expected, content)
    }

    @Test
    @Throws(IOException::class)
    fun testAddLicensesFromPom_withoutDuplicate() {
        val deps1 = getResourceFile("dependencies/groupA/deps1.pom")
        val name1 = "deps1"
        val group1 = "groupA"
        licensesTask.addLicensesFromPom(deps1, group1, name1)

        val deps2 = getResourceFile("dependencies/groupB/bcd/deps2.pom")
        val name2 = "deps2"
        val group2 = "groupB"
        licensesTask.addLicensesFromPom(deps2, group2, name2)

        val content = String(Files.readAllBytes(licensesTask.licenses.toPath()), UTF_8)
        val expected =
            ("http://www.opensource.org/licenses/mit-license.php"
                    + LINE_BREAK
                    + "https://www.apache.org/licenses/LICENSE-2.0"
                    + LINE_BREAK)

        assertEquals(licensesTask.licensesMap.size, 2)
        licensesTask.licensesMap.forEach {
            print("${it.key} -> ${it.value}")
        }
        assertTrue(licensesTask.licensesMap.containsKey("groupA:deps1"))
        assertTrue(licensesTask.licensesMap.containsKey("groupB:deps2"))
        assertEquals(expected, content)
    }

    @Test
    @Throws(IOException::class)
    fun testAddLicensesFromPom_withMultiple() {
        val deps1 = getResourceFile("dependencies/groupA/deps1.pom")
        val name1 = "deps1"
        val group1 = "groupA"
        licensesTask.addLicensesFromPom(deps1, group1, name1)

        val deps2 = getResourceFile("dependencies/groupE/deps5.pom")
        val name2 = "deps5"
        val group2 = "groupE"
        licensesTask.addLicensesFromPom(deps2, group2, name2)

        val content = String(Files.readAllBytes(licensesTask.licenses.toPath()), UTF_8)
        val expected =
            ("http://www.opensource.org/licenses/mit-license.php"
                    + LINE_BREAK
                    + "https://www.apache.org/licenses/LICENSE-2.0"
                    + LINE_BREAK)

        assertEquals(licensesTask.licensesMap.size, 3)
        assertTrue(licensesTask.licensesMap.containsKey("groupA:deps1"))
        assertTrue(licensesTask.licensesMap.containsKey("groupE:deps5 MIT License"))
        assertTrue(licensesTask.licensesMap.containsKey("groupE:deps5 Apache License 2.0"))
        assertEquals(expected, content)
    }

    @Test
    @Throws(IOException::class)
    fun testAddLicensesFromPom_withDuplicate() {
        val deps1 = getResourceFile("dependencies/groupA/deps1.pom")
        val name1 = "deps1"
        val group1 = "groupA"
        licensesTask.addLicensesFromPom(deps1, group1, name1)

        val deps2 = getResourceFile("dependencies/groupA/deps1.pom")
        val name2 = "deps1"
        val group2 = "groupA"
        licensesTask.addLicensesFromPom(deps2, group2, name2)

        val content = String(Files.readAllBytes(licensesTask.licenses.toPath()), UTF_8)
        val expected = "http://www.opensource.org/licenses/mit-license.php" + LINE_BREAK

        assertEquals(licensesTask.licensesMap.size, 1)
        assertTrue(licensesTask.licensesMap.containsKey("groupA:deps1"))
        assertEquals(expected, content)
    }

    private fun getResourceFile(resourcePath: String?): File {
        return File(javaClass.getClassLoader().getResource(resourcePath).file)
    }

    @Test
    @Throws(IOException::class)
    fun testGetBytesFromInputStream_throwException() {
        val inputStream: InputStream = mockk()
        every {
            inputStream.skip(any())
        } returns 0
        every {
            inputStream.read(any(), any(), any())
        } throws IOException()
        try {
            LicensesTask.getBytesFromInputStream(inputStream, 1, 1)
            Assert.fail("This test should throw Exception.")
        } catch (e: RuntimeException) {
            assertEquals("Failed to read license text.", e.message)
        }
    }

    @Test
    fun testGetBytesFromInputStream_normalText() {
        val test = "test"
        val inputStream: InputStream = ByteArrayInputStream(test.toByteArray(UTF_8))
        val content = String(LicensesTask.getBytesFromInputStream(inputStream, 1, 1), UTF_8)
        assertEquals("e", content)
    }

    @Test
    fun testGetBytesFromInputStream_specialCharacters() {
        val test = "Copyright © 1991-2017 Unicode"
        val inputStream: InputStream = ByteArrayInputStream(test.toByteArray(UTF_8))
        val content = String(LicensesTask.getBytesFromInputStream(inputStream, 4, 18), UTF_8)
        assertEquals("right © 1991-2017", content)
    }

//    @Test
//    @Throws(IOException::class)
//    fun testAddGooglePlayServiceLicenses() {
//        val tempOutput: File = File(licensesTask.rawResourceDir, "dependencies/groupC")
//        tempOutput.mkdirs()
//        createLicenseZip(tempOutput.path + "play-services-foo-license.aar")
//        val artifact = File(tempOutput.path + "play-services-foo-license.aar")
//        licensesTask.addGooglePlayServiceLicenses(artifact)
//
//        val content = String(Files.readAllBytes(licensesTask.licenses.toPath()), UTF_8)
//        val expected = "safeparcel" + LINE_BREAK + "JSR 305" + LINE_BREAK
//        assertEquals(expected, content)
//        assertEquals(licensesTask.googleServiceLicenses.size(), 2)
//        assertTrue(licensesTask.googleServiceLicenses.contains("safeparcel"))
//        assertTrue(licensesTask.googleServiceLicenses.contains("JSR 305"))
//        assertEquals(licensesTask.licensesMap.size(), 2)
//        assertTrue(licensesTask.licensesMap.containsKey("safeparcel"))
//        assertTrue(licensesTask.licensesMap.containsKey("JSR 305"))
//    }
//
//    @Test
//    @Throws(IOException::class)
//    fun testAddGooglePlayServiceLicenses_withoutDuplicate() {
//        val groupC: File = File(licensesTask.rawResourceDir, "dependencies/groupC")
//        groupC.mkdirs()
//        createLicenseZip(groupC.path + "/play-services-foo-license.aar")
//        val artifactFoo = File(groupC.path + "/play-services-foo-license.aar")
//
//        val groupD: File = File(licensesTask.rawResourceDir, "dependencies/groupD")
//        groupD.mkdirs()
//        createLicenseZip(groupD.path + "/play-services-bar-license.aar")
//        val artifactBar = File(groupD.path + "/play-services-bar-license.aar")
//
//        licensesTask.addGooglePlayServiceLicenses(artifactFoo)
//        licensesTask.addGooglePlayServiceLicenses(artifactBar)
//
//        val content = String(Files.readAllBytes(licensesTask.licenses.toPath()), UTF_8)
//        val expected = "safeparcel" + LINE_BREAK + "JSR 305" + LINE_BREAK
//        assertEquals(expected, content)
//        assertEquals(licensesTask.googleServiceLicenses.size(), 2)
//        assertTrue(licensesTask.googleServiceLicenses.contains("safeparcel"))
//        assertTrue(licensesTask.googleServiceLicenses.contains("JSR 305"))
//        assertEquals(licensesTask.licensesMap.size(), 2)
//        assertTrue(licensesTask.licensesMap.containsKey("safeparcel"))
//        assertTrue(licensesTask.licensesMap.containsKey("JSR 305"))
//    }

    companion object {
        private val UTF_8: Charset = StandardCharsets.UTF_8
        private const val BASE_DIR = "src/test/resources"
        private val LINE_BREAK = System.lineSeparator()

        @Throws(IOException::class)
        private fun createLicenseZip(name: String) {
            val zipFile = File(name)
            val output = ZipOutputStream(FileOutputStream(zipFile))
            val input = File("$BASE_DIR/sampleLicenses")
            for (file in input.listFiles()) {
                val entry = ZipEntry(file.getName())
                val bytes = Files.readAllBytes(file.toPath())
                output.putNextEntry(entry)
                output.write(bytes, 0, bytes.size)
                output.closeEntry()
            }
            output.close()
        }
    }
}