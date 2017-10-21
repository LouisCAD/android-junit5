package de.mannodermaus.gradle.plugins.android_junit5

import org.gradle.api.Project

/*
 * Unit testing the integration of JUnit 5
 * with the Android Gradle Plugin version 3.
 */

class AGP3PluginSpec extends BasePluginSpec {

  def "Application: Custom Product Flavors"() {
    when:
    Project project = factory.newProject(rootProject())
        .asAndroidApplication()
        .applyJunit5Plugin()
        .build()

    project.android {
      // "All flavors must now belong to a named flavor dimension"
      flavorDimensions "price"

      productFlavors {
        free { dimension "price" }
        paid { dimension "price" }
      }
    }

    project.evaluate()

    then:
    // These statements automatically assert the existence of the tasks,
    // and raise an Exception if absent
    def runDebugFree = project.tasks.getByName("junitPlatformTestFreeDebug")
    def runDebugPaid = project.tasks.getByName("junitPlatformTestPaidDebug")
    def runReleaseFree = project.tasks.getByName("junitPlatformTestFreeRelease")
    def runReleasePaid = project.tasks.getByName("junitPlatformTestPaidRelease")
    def runAll = project.tasks.getByName("junitPlatformTest")

    // Assert that dependency chain is valid
    assert runAll.getDependsOn()
        .containsAll([runDebugFree, runDebugPaid, runReleaseFree, runReleasePaid])
  }

  def "Application: Jacoco Integration with Product Flavors"() {
    when:
    Project project = factory.newProject(rootProject())
        .asAndroidApplication()
        .applyJunit5Plugin()
        .applyJacocoPlugin()
        .build()

    project.android {
      // "All flavors must now belong to a named flavor dimension"
      flavorDimensions "price"

      productFlavors {
        free { dimension "price" }
        paid { dimension "price" }
      }
    }

    project.evaluate()

    then:
    // These statements automatically assert the existence of the tasks,
    // and raise an Exception if absent
    def runDebugFree = project.tasks.getByName("jacocoTestReportFreeDebug")
    def runDebugPaid = project.tasks.getByName("jacocoTestReportPaidDebug")
    def runReleaseFree = project.tasks.getByName("jacocoTestReportFreeRelease")
    def runReleasePaid = project.tasks.getByName("jacocoTestReportPaidRelease")
    def runAll = project.tasks.getByName("jacocoTestReport")

    // Assert that dependency chain is valid
    assert runAll.getDependsOn()
        .containsAll([runDebugFree, runDebugPaid, runReleaseFree, runReleasePaid])
  }
}