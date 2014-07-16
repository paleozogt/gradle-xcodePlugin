package org.openbakery

import org.gmock.GMockController
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

/**
 * Created by rene on 01.07.14.
 */
class XcodeTestTaskTest {



	Project project
	XcodeTestTask xcodeTestTask

	GMockController mockControl
	CommandRunner commandRunnerMock

	@BeforeMethod
	def setup() {
		/*
		mockControl = new GMockController()
		commandRunnerMock = mockControl.mock(CommandRunner)


*/

		project = ProjectBuilder.builder().build()
		project.buildDir = new File('build').absoluteFile
		project.apply plugin: org.openbakery.XcodePlugin


		xcodeTestTask = project.tasks.findByName('test')
		//xcodeTestTask.setProperty("commandRunner", commandRunnerMock)

	}

	TestResult testResult(String name, boolean success) {
		TestResult testResult = new TestResult()
		testResult.success = success
		testResult.duration = 0.1
		testResult.method = name
		return testResult;
	}

	@Test
	void testXMLOuput() {


		Destination destination = new Destination()
		destination.platform = "iPhoneSimulator"
		destination.name = "iPad"
		destination.arch = "i386"
		destination.id = "iPad Retina"
		destination.os = "iOS"

		project.xcodebuild.destinations = []
		project.xcodebuild.destinations << destination;



		TestClass testClass = new TestClass();
		testClass.name = "HelloWorldTest"
		for (int i=0; i<5; i++) {
			testClass.results << testResult("testSuccess_" + i, true)
		}
		for (int i=0; i<3; i++) {
			testClass.results << testResult("testError_" + i, false)
		}

		def allResults = [:]
		def resultList = []
		resultList << testClass

		allResults.put(destination, resultList)


		xcodeTestTask.store(allResults)


	}


}
