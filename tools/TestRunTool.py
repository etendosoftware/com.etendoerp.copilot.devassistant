from typing import Type, Dict, List, Union
from copilot.core.tool_input import ToolField, ToolInput
from copilot.core.tool_wrapper import ToolWrapper
import subprocess
import os


class TestRunToolInput(ToolInput):
    test_targets: Union[str, List[str]] = ToolField(
        title="Test Targets",
        description=("Specify one or more tests or packages to execute. For example:\n"
                     "- A specific test: 'com.etendoerp.webhookevents.WebhookSetupTest'\n"
                     "- Multiple specific tests: ['com.etendoerp.webhookevents.WebhookSetupTest', "
                     "'com.etendoerp.copilot.rest.CopilotJwtServletTest']\n"
                     "- A package: 'com.etendoerp.copilot.*'\n"
                     "- Multiple packages: ['com.etendoerp.webhookevents.*', 'com.etendoerp.copilot.*']\n"
                     "- All tests: leave empty.")
    )
    working_directory: str = ToolField(
        title="Working Directory",
        description="The path to the directory where the `gradlew` script is located."
    )


class TestRunTool(ToolWrapper):
    name: str = "TestRunTool"
    description: str = """
        A tool to execute Java tests using Gradle.
        Example input:
        {
            "test_targets": ["com.etendoerp.webhookevents.WebhookSetupTest"],
            "working_directory": "/path/to/erp"
        }
        """
    args_schema: Type[ToolInput] = TestRunToolInput

    def run(self, input_params: Dict, *args, **kwargs):
        test_targets = input_params.get('test_targets', [])
        working_directory = input_params.get('working_directory')

        if not working_directory or not os.path.isdir(working_directory):
            return {"error": f"Invalid working directory: {working_directory}"}

        # Build the Gradle command
        command = './gradlew test'
        if test_targets:
            if isinstance(test_targets, str):
                test_targets = [test_targets]
            for target in test_targets:
                command += f' --tests "{target}"'

        try:
            # Execute the Gradle command in the specified working directory
            process = subprocess.run(
                command,
                cwd=working_directory,
                shell=True,
                check=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True
            )
            output = process.stdout

            # Analyze the test output
            return self._analyze_test_results(output, success=True, working_directory=working_directory)
        except subprocess.CalledProcessError as e:
            return self._analyze_test_results(e.stdout + e.stderr, success=False, working_directory=working_directory)

    def _analyze_test_results(self, logs: str, success: bool, working_directory: str) -> Dict:
        # Initialize results structure
        results = {
            "summary": None,
            "passed_tests": [],
            "failed_tests": []
        }

        # Check build status
        if success:
            results["summary"] = "All tests passed successfully."
        else:
            results["summary"] = "Some tests failed. Review the details below."

        # Parse logs for test details
        for line in logs.splitlines():
            if "FAILED" in line and ">" in line:
                results["failed_tests"].append(line.strip())
            elif "PASSED" in line and ">" in line:
                results["passed_tests"].append(line.strip())

        # Additional notes for all tests execution
        if "BUILD FAILED" in logs and not results["failed_tests"]:
            results["summary"] += " It seems like a configuration or setup issue. Check the logs for more details."

        response = {
            "summary": results["summary"],
            "details": {
                "passed_tests": results["passed_tests"],
                "failed_tests": results["failed_tests"]
            }
        }

        # Include raw logs in case of failure
        if not success:
            response["raw_logs"] = logs

        return response
