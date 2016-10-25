package org.openbakery

import org.apache.commons.io.FileUtils
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.openbakery.output.XcodeBuildOutputAppender
import org.openbakery.testdouble.PlistHelperStub
import org.openbakery.testdouble.SimulatorControlStub
import org.openbakery.testdouble.XcodeFake
import org.openbakery.xcode.DestinationResolver
import org.openbakery.xcode.Type
import org.openbakery.xcode.Xcodebuild
import spock.lang.Specification

import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Created by rene on 25.10.16.
 */
class XcodeBuildForTestTaskSpecification extends Specification {

	Project project
	CommandRunner commandRunner = Mock(CommandRunner);


	XcodeBuildForTestTask xcodeBuildForTestTask

	def setup() {
		project = ProjectBuilder.builder().build()
		project.buildDir = new File(System.getProperty("java.io.tmpdir"), "gradle-xcodebuild/build")

		project.apply plugin: org.openbakery.XcodePlugin

		xcodeBuildForTestTask = project.getTasks().getByPath(XcodePlugin.XCODE_BUILD_FOR_TEST_TASK_NAME)
		xcodeBuildForTestTask.commandRunner = commandRunner
		xcodeBuildForTestTask.xcode = new XcodeFake()
		xcodeBuildForTestTask.destinationResolver = new DestinationResolver(new SimulatorControlStub("simctl-list-xcode8.txt"))
		project.xcodebuild.plistHelper = new PlistHelperStub()

	}

	def cleanup() {
		FileUtils.deleteDirectory(project.projectDir)
	}

	def "instance is of type XcodeBuildForTestTask"() {
		expect:
		xcodeBuildForTestTask instanceof  XcodeBuildForTestTask

	}


	def "depends on"() {
		when:
		def dependsOn = xcodeBuildForTestTask.getDependsOn()

		then:
		dependsOn.size() == 3
		dependsOn.contains(XcodePlugin.XCODE_CONFIG_TASK_NAME)
		dependsOn.contains(XcodePlugin.SIMULATORS_KILL_TASK_NAME)
	}


	def "has xcodebuild"() {
		expect:
		xcodeBuildForTestTask.xcodebuild instanceof Xcodebuild
	}


	def "xcodebuild has merged parameters"() {
		when:
		project.xcodebuild.target = "Test"

		xcodeBuildForTestTask.buildForTest()

		then:
		xcodeBuildForTestTask.parameters.target == "Test"
	}

	def "output directory is build-for-testing"() {
		def givenOutputFile

		when:
		project.xcodebuild.target = "Test"
		xcodeBuildForTestTask.buildForTest()

		then:
		1 * commandRunner.setOutputFile(_) >> { arguments -> givenOutputFile = arguments[0] }
		givenOutputFile == new File(project.getBuildDir(), "for-testing/xcodebuild-output.txt")
	}


	def "IllegalArgumentException_when_no_scheme_or_target_given"() {
		when:
		xcodeBuildForTestTask.buildForTest()

		then:
		thrown(IllegalArgumentException)
	}


	def "xcodebuild is exectued"() {
		def commandList
		project.xcodebuild.scheme = 'myscheme'

		when:
		xcodeBuildForTestTask.buildForTest()

		then:
		1 * commandRunner.run(_, _, _, _) >> { arguments -> commandList = arguments[1] }
		commandList.contains("build-for-testing")
		commandList.contains("myscheme")

	}

	def "has output appender"() {
		def outputAppender
		project.xcodebuild.scheme = 'myscheme'

		when:
		xcodeBuildForTestTask.buildForTest()

		then:
		1 * commandRunner.run(_, _, _, _) >> { arguments -> outputAppender = arguments[3] }
		outputAppender instanceof XcodeBuildOutputAppender
	}

	def "xcodebuild has all available iOS destinations"() {
		when:
		xcodeBuildForTestTask.parameters.simulator = true
		xcodeBuildForTestTask.parameters.type = Type.iOS
		def destinations = xcodeBuildForTestTask.xcodebuild.destinations

		then:
		destinations.size() == 14
		destinations[0].platform == "iOS Simulator"

	}

	def "xcodebuild has all available tvOS destinations"() {
		when:
		xcodeBuildForTestTask.parameters.type = Type.tvOS
		def destinations = xcodeBuildForTestTask.xcodebuild.destinations

		then:
		destinations.size() == 1
		destinations[0].platform == "tvOS Simulator"
	}

	def "xcodebuild has all available iOS device destinations"() {
		when:
		xcodeBuildForTestTask.parameters.type = Type.iOS
		xcodeBuildForTestTask.parameters.simulator = false
		def destinations = xcodeBuildForTestTask.xcodebuild.destinations

		then:
		destinations.size() == 0
	}


	def "has test bundle as result for iOS Simulator"() {
		project.xcodebuild.target = 'myscheme'
		project.xcodebuild.productName = 'Example'
		xcodeBuildForTestTask.parameters.simulator = true
		xcodeBuildForTestTask.parameters.type = Type.iOS

		when:
		xcodeBuildForTestTask.buildForTest()

		then:
		new File(project.getBuildDir(), "for-testing/Example-iOS-Simulator.testbundle.zip").exists()
	}

	def "has test bundle as result for tvOS"() {
		project.xcodebuild.target = 'myscheme'
		project.xcodebuild.productName = 'Example'
		xcodeBuildForTestTask.parameters.simulator = true
		xcodeBuildForTestTask.parameters.type = Type.tvOS

		when:
		xcodeBuildForTestTask.buildForTest()

		then:
		new File(project.getBuildDir(), "for-testing/Example-tvOS-Simulator.testbundle.zip").exists()
	}

	def "has test bundle as result for iOS Device"() {
		project.xcodebuild.target = 'myscheme'
		project.xcodebuild.productName = 'Example'
		xcodeBuildForTestTask.parameters.simulator = false
		xcodeBuildForTestTask.parameters.type = Type.iOS

		when:
		xcodeBuildForTestTask.buildForTest()

		then:
		new File(project.getBuildDir(), "for-testing/Example-iOS.testbundle.zip").exists()
	}



	def "has app in test bundle"() {
		given:
		project.xcodebuild.target = 'myscheme'
		project.xcodebuild.productName = 'Example'
		xcodeBuildForTestTask.parameters.simulator = true
		xcodeBuildForTestTask.parameters.type = Type.iOS

		File symDirectory = new File(project.getBuildDir(), "sym")
		File sym = TestHelper.createDummyApp(new File(symDirectory, "Test-iphonesimulator"), "Example")


		File xctestrun = new File("src/test/Resource/Example_iphonesimulator.xctestrun")
		FileUtils.copyFile(xctestrun, new File(symDirectory, "Example_iphonesimulator.xctestrun"))


		xcodeBuildForTestTask.parameters.symRoot = sym

		when:
		xcodeBuildForTestTask.buildForTest()

		File zipBundle = new File(project.getBuildDir(), "for-testing/Example-iOS-Simulator.testbundle.zip")
		ZipFile zip = new ZipFile(zipBundle);
		List<String> entries = new ArrayList<String>()
		for (ZipEntry entry : zip.entries()) {
			entries.add(entry.getName())
		}

		then:
		zipBundle.exists()
		entries.contains("Example-iOS-Simulator.testbundle/Example.app/")
		entries.contains("Example-iOS-Simulator.testbundle/Example_iphonesimulator.xctestrun")
	}


}
