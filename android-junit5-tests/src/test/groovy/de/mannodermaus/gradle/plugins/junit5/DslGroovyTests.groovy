package de.mannodermaus.gradle.plugins.junit5

import de.mannodermaus.gradle.plugins.junit5.util.TestEnvironment
import de.mannodermaus.gradle.plugins.junit5.util.TestProjectFactory
import kotlin.io.FilesKt
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName

import static org.assertj.core.api.Assertions.assertThat

class DslGroovyTests {

  private TestProjectFactory factory
  private Project testRoot

  @BeforeEach
  void beforeEach() {
    def environment = new TestEnvironment()

    this.factory = new TestProjectFactory(environment)
    this.testRoot = factory.newRootProject()
  }

  @AfterEach
  void afterEach() {
    FilesKt.deleteRecursively(this.testRoot.rootDir)
  }

  @org.junit.jupiter.api.Test
  @DisplayName("dynamic filters methods can be called on existing build types")
  void dynamicFiltersMethodsCanBeCalledOnExistingBuildTypes() {
    def project = factory.newProject(testRoot)
        .asAndroidApplication()
        .build()

    project.android.testOptions.junitPlatform {
      filters {
        includeTags "some-tag"
        excludeTags "other-tag"
      }
      debugFilters {
        includeTags "debug-tag"
      }
    }

    project.evaluate()

    def debugTask = project.tasks.getByName("testDebugUnitTest") as Test
    assertThat(debugTask.testFramework.options.includeTags).containsOnly("some-tag", "debug-tag")
    assertThat(debugTask.testFramework.options.excludeTags).containsOnly("other-tag")

    def releaseTask = project.tasks.getByName("testReleaseUnitTest") as Test
    assertThat(releaseTask.testFramework.options.includeTags).containsOnly("some-tag")
    assertThat(releaseTask.testFramework.options.excludeTags).containsOnly("other-tag")
  }

  @org.junit.jupiter.api.Test
  @DisplayName("dynamic filters methods can be called on existing product flavors")
  void dynamicFiltersMethodsCanBeCalledOnExistingProductFlavors() {
    def project = factory.newProject(testRoot)
        .asAndroidApplication()
        .build()

    project.android {
      flavorDimensions "tier"
      productFlavors {
        free {
          dimension "tier"
        }
        paid {
          dimension "tier"
        }
      }

      testOptions.junitPlatform {
        filters {
          includeTags "some-tag"
          excludeTags "other-tag"
        }
        freeDebugFilters {
          includeTags "free-debug-tag"
        }
        paidFilters {
          includeTags "paid-tag"
        }
        releaseFilters {
          includeTags "release-tag"
        }
      }
    }

    project.evaluate()

    def freeDebugTask = project.tasks.getByName("testFreeDebugUnitTest") as Test
    assertThat(freeDebugTask.testFramework.options.includeTags).
        containsOnly("some-tag", "free-debug-tag")
    assertThat(freeDebugTask.testFramework.options.excludeTags).containsOnly("other-tag")

    def freeReleaseTask = project.tasks.getByName("testFreeReleaseUnitTest") as Test
    assertThat(freeReleaseTask.testFramework.options.includeTags).
        containsOnly("some-tag", "release-tag")
    assertThat(freeReleaseTask.testFramework.options.excludeTags).containsOnly("other-tag")

    def paidDebugTask = project.tasks.getByName("testPaidDebugUnitTest") as Test
    assertThat(paidDebugTask.testFramework.options.includeTags).containsOnly("some-tag", "paid-tag")
    assertThat(paidDebugTask.testFramework.options.excludeTags).containsOnly("other-tag")

    def paidReleaseTask = project.tasks.getByName("testPaidReleaseUnitTest") as Test
    assertThat(paidReleaseTask.testFramework.options.includeTags).
        containsOnly("some-tag", "paid-tag", "release-tag")
    assertThat(paidReleaseTask.testFramework.options.excludeTags).containsOnly("other-tag")
  }

  @org.junit.jupiter.api.Test
  @DisplayName("complex example with multiple flavor dimensions & build types")
  void complexExampleWithMultipleFlavorDimensionsAndBuildTypes() {
    def project = factory.newProject(testRoot)
        .asAndroidApplication()
        .build()

    project.android {
      flavorDimensions "brand", "environment", "payment"
      productFlavors {
        brandA {
          dimension "brand"
        }
        brandB {
          dimension "brand"
        }

        development {
          dimension "environment"
        }
        production {
          dimension "environment"
        }

        free {
          dimension "payment"
        }
        paid {
          dimension "payment"
        }
      }

      buildTypes {
        ci {
          initWith debug
        }
      }

      testOptions.junitPlatform {
        filters {
          includeTags "global-tag"
        }

        brandAFilters {
          includeTags "brandA-tag"
          includeTags "some-other-tag"
        }

        developmentFilters {
          includeTags "development-tag"
        }

        paidFilters {
          includeTags "paid-tag"
          excludeTags "some-other-tag"
        }
      }
    }

    project.evaluate()

    def brandADevelopmentPaidDebugTask = project.tasks
        .getByName("testBrandADevelopmentPaidDebugUnitTest") as Test

    assertThat(brandADevelopmentPaidDebugTask.testFramework.options.includeTags).
        containsOnly("global-tag", "brandA-tag", "development-tag", "paid-tag")
    assertThat(brandADevelopmentPaidDebugTask.testFramework.options.excludeTags).
        containsOnly("some-other-tag")
  }
}
