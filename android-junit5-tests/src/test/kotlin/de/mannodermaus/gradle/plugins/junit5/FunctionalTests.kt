@file:Suppress("UnusedImport")

package de.mannodermaus.gradle.plugins.junit5

import de.mannodermaus.gradle.plugins.junit5.util.*
import de.mannodermaus.gradle.plugins.junit5.util.FileLanguage.Java
import de.mannodermaus.gradle.plugins.junit5.util.FileLanguage.Kotlin
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import org.junitpioneer.jupiter.TempDirectory
import org.junitpioneer.jupiter.TempDirectory.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Created by Marcel Schnelle on 2018/06/19.
 * Copyright © 2018 TenTen Technologies Limited. All rights reserved.
 */
@OnlyOnLocalMachine
@ExtendWith(TempDirectory::class)
class FunctionalTests {

  companion object {
    private val SUPPORTED_GRADLE_VERSIONS = listOf("4.7", "5.0")
    private val SUPPORTED_LANGUAGES = FileLanguage.values().toList()

    // Combine each tested Gradle version with a File Language for these tests
    val ALL_VARIATIONS = (SUPPORTED_GRADLE_VERSIONS * SUPPORTED_LANGUAGES).toList()
  }

  private lateinit var testProjectDir: File
  private lateinit var buildFile: File
  private lateinit var pluginClasspath: List<File>
  private lateinit var testCompileClasspath: List<File>

  private val environment = TestEnvironment()

  /* Lifecycle */

  // Sets up a fresh temporary folder containing a project structure.
  // This cannot be a @BeforeEach method, since this class uses dynamic tests
  // which require a distinct folder, and they don't partake in the lifecycle.
  private fun setupNewProject(testProjectDir: File) {
    this.testProjectDir = testProjectDir
    this.pluginClasspath = loadClassPathManifestResource("plugin-classpath.txt")
    this.testCompileClasspath = loadClassPathManifestResource(
        "functional-test-compile-classpath.txt")

    // Write expected values to local.properties
    testProjectDir.newFile("local.properties").writeText("""
      sdk.dir=${environment.androidSdkFolder.absolutePath}
      """)

    // Write environment settings to gradle.properties
    testProjectDir.newFile("gradle.properties").writeText("""
      org.gradle.jvmargs=-Xmx1024m -XX:MaxPermSize=256m -XX:+HeapDumpOnOutOfMemoryError
    """)

    // Create and prepare build file
    this.buildFile = testProjectDir.newFile("build.gradle")
    this.buildFile.appendText("""
      buildscript {
        dependencies {
          classpath files(${ClasspathSplitter.splitClasspath(pluginClasspath)})
        }
      }
      """)

    // Create the main source file, on which most tests operate
    val sourceFilePath = Paths.get(
        testProjectDir.toString(),
        *"src/main/java/de/mannodermaus/app/Adder.java".splitToArray())
    Files.createDirectories(sourceFilePath.parent)
    sourceFilePath.toFile().writeText("""
            package de.mannodermaus.app;

            public class Adder {
              public int add(int a, int b) {
                return a + b;
              }
            }
          """)
  }

  /* Tests */

  @TestFactory
  fun `Executes tests in default source set`(@TempDir testProjectDir: Path) =
      ALL_VARIATIONS
          .map { (gradleVersion, language) ->
            dynamicTest("Using $language & Gradle $gradleVersion") {
              // Create a new folder per variation (@BeforeEach doesn't work in dynamic tests)
              val folder = testProjectDir.newFile("$language-$gradleVersion")
              setupNewProject(folder)

              given {
                plugins {
                  android()
                  if (language == Kotlin) kotlin()
                  junit5()
                }
                testSources(language) {
                  test()
                }
              }

              runGradle(version = gradleVersion) { result ->
                listOf(
                    // Assert that all tasks ran successfully
                    { assertThat(result).executedTaskSuccessfully(":build") },
                    { assertThat(result).executedTaskSuccessfully(":testDebugUnitTest") },
                    { assertThat(result).executedTaskSuccessfully(":testReleaseUnitTest") },

                    // Assert number of tests executed (1 per Build Type)
                    { assertThat(result).executedTestSuccessfully("${language}Test", times = 2) }
                )
              }
            }
          }

  @TestFactory
  fun `Executes tests in build-type-specific source set`(@TempDir testProjectDir: Path) =
      ALL_VARIATIONS
          .map { (gradleVersion, language) ->
            dynamicTest("Using $language & Gradle $gradleVersion") {
              // Create a new folder per variation (@BeforeEach doesn't work in dynamic tests)
              val folder = testProjectDir.newFile("$language-$gradleVersion")
              setupNewProject(folder)

              given {
                plugins {
                  android()
                  if (language == Kotlin) kotlin()
                  junit5()
                }
                testSources(language) {
                  test()
                  test(buildType = "debug")
                }

                runGradle(version = gradleVersion, tasks = "testDebugUnitTest") { result ->
                  listOf(
                      { assertThat(result).executedTaskSuccessfully(":testDebugUnitTest") },
                      { assertThat(result).executedTestSuccessfully("${language}DebugTest") },
                      { assertThat(result).executedTestSuccessfully("${language}Test") }
                  )
                }

                runGradle(version = gradleVersion, tasks = "testReleaseUnitTest") { result ->
                  listOf(
                      { assertThat(result).executedTaskSuccessfully(":testReleaseUnitTest") },
                      { assertThat(result).executedTestSuccessfully("${language}Test") }
                  )
                }
              }
            }
          }

  @TestFactory
  fun `Executes tests in flavor-specific source set`(@TempDir testProjectDir: Path) =
      ALL_VARIATIONS
          .map { (gradleVersion, language) ->
            dynamicTest("Using $language & Gradle $gradleVersion") {
              // Create a new folder per variation (@BeforeEach doesn't work in dynamic tests)
              val folder = testProjectDir.newFile("$language-$gradleVersion")
              setupNewProject(folder)

              given {
                plugins {
                  android(flavorNames = listOf("free"))
                  if (language == Kotlin) kotlin()
                  junit5()
                }
                testSources(language) {
                  test()
                  test(flavorName = "free")
                }
              }

              runGradle(version = gradleVersion) { result ->
                listOf(
                    { assertThat(result).executedTaskSuccessfully(":build") },
                    { assertThat(result).executedTaskSuccessfully(":testFreeDebugUnitTest") },
                    { assertThat(result).executedTaskSuccessfully(":testFreeReleaseUnitTest") },
                    { assertThat(result).executedTestSuccessfully("${language}FreeTest", times = 2) },
                    { assertThat(result).executedTestSuccessfully("${language}Test", times = 2) }
                )
              }
            }
          }

  @TestFactory
  fun `Executes tests in build-type-and-flavor-specific source set`(@TempDir testProjectDir: Path) =
      ALL_VARIATIONS
          .map { (gradleVersion, language) ->
            dynamicTest("Using $language & Gradle $gradleVersion") {
              // Create a new folder per variation (@BeforeEach doesn't work in dynamic tests)
              val folder = testProjectDir.newFile("$language-$gradleVersion")
              setupNewProject(folder)

              given {
                plugins {
                  android(flavorNames = listOf("free"))
                  if (language == Kotlin) kotlin()
                  junit5()
                }
                testSources(language) {
                  test()
                  test(buildType = "debug")
                  test(flavorName = "free", buildType = "debug")
                  test(buildType = "release")
                }

                runGradle(version = gradleVersion, tasks = "testFreeDebugUnitTest") { result ->
                  listOf(
                      { assertThat(result).executedTaskSuccessfully(":testFreeDebugUnitTest") },
                      { assertThat(result).executedTestSuccessfully("${language}FreeDebugTest") },
                      { assertThat(result).executedTestSuccessfully("${language}DebugTest") },
                      { assertThat(result).executedTestSuccessfully("${language}Test") }
                  )
                }

                runGradle(version = gradleVersion, tasks = "testFreeReleaseUnitTest") { result ->
                  listOf(
                      { assertThat(result).executedTaskSuccessfully(":testFreeReleaseUnitTest") },
                      { assertThat(result).executedTestSuccessfully("${language}ReleaseTest") },
                      { assertThat(result).executedTestSuccessfully("${language}Test") }
                  )
                }
              }
            }
          }

  @Test
  fun `Returns default values successfully`(@TempDir testProjectDir: Path) {
    setupNewProject(testProjectDir.toFile())

    given {
      plugins {
        android()
        junit5(
            testOptionsConfig = """
              unitTests {
                returnDefaultValues = true
              }
            """
        )
      }
      testSources(Java) {
        test(
            content = """
              package de.mannodermaus.app;

              import static org.junit.jupiter.api.Assertions.assertNull;

              import org.junit.jupiter.api.Test;
              import android.content.Intent;

              class AndroidTest {
                @Test
                void test() {
                  Intent intent = new Intent();
                  assertNull(intent.getAction());
                }
              }
            """
        )
      }

      runGradle("testDebugUnitTest") { result ->
        listOf(
            { assertThat(result).executedTaskSuccessfully(":testDebugUnitTest") },
            { assertThat(result).executedTestSuccessfully("AndroidTest") }
        )
      }
    }
  }

  @Test
  fun `Includes Android resources successfully`(@TempDir testProjectDir: Path) {
    setupNewProject(testProjectDir.toFile())

    given {
      plugins {
        android()
        junit5(
            testOptionsConfig = """
              unitTests {
                includeAndroidResources = true
              }
            """
        )
      }
      testSources(Java) {
        test(
            content = """
              package de.mannodermaus.app;

              import static org.junit.jupiter.api.Assertions.assertNotNull;

              import org.junit.jupiter.api.Test;
              import java.io.InputStream;

              class AndroidTest {
                @Test
                void test() {
                  InputStream is = getClass().getResourceAsStream("/com/android/tools/test_config.properties");
                  assertNotNull(is);
                }
              }
            """
        )
      }
    }

    runGradle("testDebugUnitTest") { result ->
      listOf(
          { assertThat(result).executedTaskSuccessfully(":testDebugUnitTest") },
          { assertThat(result).executedTestSuccessfully("AndroidTest") }
      )
    }
  }

  /* Private */

  private fun loadClassPathManifestResource(name: String): List<File> {
    val classpathResource = javaClass.classLoader.getResourceAsStream(name)
        ?: throw IllegalStateException("Did not find required resource with name $name")

    return classpathResource.bufferedReader()
        .lineSequence()
        .map { File(it) }
        .toList()
  }

  private fun given(configuration: Given.() -> Unit) {
    configuration(Given())
  }

  private fun runGradle(tasks: String = "build",
                        version: String? = null,
                        assertions: (BuildResult) -> List<() -> Unit>) {
    val buildResult = GradleRunner.create()
        .withProjectDir(testProjectDir)
        .withPluginClasspath(pluginClasspath)
        .withArguments(tasks)
        .apply {
          if (version != null) {
            withGradleVersion(version)
          }
        }
        .build()

    assertAll(
        heading = "Gradle Execution failed. Output:\n${buildResult.output}",
        assertions = *assertions(buildResult).toTypedArray())
  }

  // Entry point to a small DSL for configuration of virtual projects
  private inner class Given {

    fun plugins(configuration: Plugins.() -> Unit) {
      configuration(Plugins())
    }

    fun testSources(language: FileLanguage, configuration: TestSources.() -> Unit) {
      configuration(TestSources(language))
    }

    inner class Plugins {

      fun android(flavorNames: List<String>? = null) {
        // Require AndroidManifest.xml
        val manifestPath = Paths.get(
            testProjectDir.toString(),
            *"src/main/AndroidManifest.xml".splitToArray("/"))
        Files.createDirectories(manifestPath.parent)
        manifestPath.toFile().writeText("""
          <manifest package="de.mannodermaus.app"/>
        """)

        // Take optional product flavors into account
        val productFlavors = if (flavorNames != null) {
          """
          flavorDimensions "tier"
            productFlavors {
              ${flavorNames.joinToString("\n") { """$it { dimension "tier" }""" }}
            }
          """
        } else {
          ""
        }

        // Write out the build file's configuration
        buildFile.appendText("""
        apply plugin: "com.android.application"

        android {
          compileSdkVersion "${environment.compileSdkVersion}"
          buildToolsVersion "${environment.buildToolsVersion}"

          defaultConfig {
            applicationId "de.mannodermaus.app"
            minSdkVersion ${environment.minSdkVersion}
            targetSdkVersion ${environment.targetSdkVersion}
            versionCode 1
            versionName "1.0"
          }

          $productFlavors

          lintOptions {
            abortOnError false
          }
        }

        // Disabled because the Lint library dependency
        // can't be resolved within the offline-only virtual project execution
        lint.enabled false

        // Required by AAPT2
        repositories {
          google()
        }

        dependencies {
          testImplementation files(${ClasspathSplitter.splitClasspath(testCompileClasspath)})
        }
        """)
      }

      fun kotlin() {
        buildFile.appendText("""
          apply plugin: "kotlin-android"

          android {
            sourceSets {
              main.java.srcDir "src/main/kotlin"
              test.java.srcDir "src/test/kotlin"
            }
          }

          dependencies {
            // Required since Kotlin 1.2.60;
            // will fail with "can't find kotlin-compiler-embeddable" if not overwritten
            kotlinCompilerClasspath files(${ClasspathSplitter.splitClasspath(testCompileClasspath)})
          }
        """)
      }

      fun junit5(junitPlatformConfig: String? = null,
                 testOptionsConfig: String? = null) {
        buildFile.appendText("""
          apply plugin: "de.mannodermaus.android-junit5"

          android.testOptions {
            junitPlatform {
              ${junitPlatformConfig ?: ""}
            }
            unitTests.all {
              it.testLogging {
                // Required to assert the Gradle output for these unit tests
                events "passed", "failed"
                exceptionFormat = "full"
              }
            }
            ${testOptionsConfig ?: ""}
          }

          dependencies {
            // Use local dependencies so that defaultDependencies are not used
            testImplementation files(${ClasspathSplitter.splitClasspath(testCompileClasspath)})
          }
        """)
      }
    }

    inner class TestSources(private val language: FileLanguage) {

      fun test(content: String? = null,
               flavorName: String = "",
               buildType: String = "") {
        when (language) {
          Java -> javaTest(content, flavorName, buildType)
          Kotlin -> kotlinTest(content, flavorName, buildType)
        }
      }

      /* Private */

      private fun createTestInternal(language: FileLanguage,
                                     content: String,
                                     flavorName: String = "",
                                     buildType: String = "") {
        val variant = "${flavorName.capitalize()}${buildType.capitalize()}"
        val testName = "${language.name}${variant}Test"
        val sourceSet = "test$variant"
        val fileName = language.appendExtension(testName)

        val filePath = Paths.get(testProjectDir.toString(),
            // e.g. "src/test/java" or "src/testFreeDebug/kotlin"
            "src", sourceSet, language.sourceDirectoryName,
            // Package name of test file
            *"de/mannodermaus/app/$fileName".splitToArray())
        Files.createDirectories(filePath.parent)

        filePath.toFile().writeText(content.replace("__NAME__", testName))
      }

      private val defaultJavaTestContent = """
          package de.mannodermaus.app;

          import static org.junit.jupiter.api.Assertions.assertEquals;

          import org.junit.jupiter.api.Test;

          class __NAME__ {
            @Test
            void test() {
              Adder adder = new Adder();
              assertEquals(4, adder.add(2, 2), "This should succeed!");
            }
          }
        """

      private fun javaTest(content: String? = null, flavorName: String = "",
                           buildType: String = "") {
        val fileContent = content ?: defaultJavaTestContent
        this.createTestInternal(language = Java,
            flavorName = flavorName,
            buildType = buildType,
            content = fileContent)
      }

      private val defaultKotlinTestContent = """
          package de.mannodermaus.app

          import org.junit.jupiter.api.Assertions.assertEquals
          import org.junit.jupiter.api.Test

          class __NAME__ {
            @Test
            fun test() {
              val adder = Adder()
              assertEquals(4, adder.add(2, 2), "This should succeed!")
            }
          }
        """

      private fun kotlinTest(content: String? = null, flavorName: String = "",
                             buildType: String = "") {
        val fileContent = content ?: defaultKotlinTestContent
        this.createTestInternal(language = Kotlin,
            flavorName = flavorName,
            buildType = buildType,
            content = fileContent)
      }
    }
  }
}
