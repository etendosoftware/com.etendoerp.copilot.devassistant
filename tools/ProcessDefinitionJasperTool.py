from typing import Dict, Type, Optional, List

from langsmith import traceable

from copilot.core.etendo_utils import call_webhook, get_etendo_token, get_etendo_host
from copilot.core.tool_input import ToolField, ToolInput
from copilot.core.tool_wrapper import ToolWrapper


class ProcessDefinitionJasperInput(ToolInput):
    i_prefix: str = ToolField(
        title="Prefix",
        description="This is the prefix of the module in the database."
    )

    i_searchkey: str = ToolField(
        title="Search Key",
        description="This is the search key of the process definition."
    )

    i_report_name: str = ToolField(
        title="Report Name",
        description="This is the user friendly name of the report."
    )

    i_help_comment: Optional[str] = ToolField(
        title="Help Comment",
        description="This is a help comment for the report.",
        default=None
    )

    i_description: Optional[str] = ToolField(
        title="Description",
        description="Description of the report.",
        default=None
    )

    i_parameters: str = ToolField(
        title="Parameters",
        description="Semicolon-separated list of parameter definitions in the format BD_NAME-NAME-LENGTH-SEQNO-REFERENCENCE."
    )

    i_report_path: str = ToolField(
        title="Report Path",
        description="Path where the report is stored."
    )


def process_parameters(parameter_string: str) -> List[Dict[str, str]]:
    """Process the parameter string to extract parameter definitions."""
    parameters = []
    param_list = parameter_string.split(';')
    for param in param_list:
        bd_name, name, length, seqno, reference = param.split('-')
        parameters.append({
            "BD_NAME": bd_name,
            "NAME": name,
            "LENGTH": length,
            "SEQNO": seqno,
            "REFERENCE": reference
        })
    return parameters


class ProcessDefinitionJasperTool(ToolWrapper):
    """This tool creates a process definition for Jasper reports in the Etendo Application Dictionary."""
    name = 'ProcessDefinitionJasperTool'
    description = "Creates a process definition for Jasper reports in the Etendo Application Dictionary."
    args_schema: Type[ToolInput] = ProcessDefinitionJasperInput

    @traceable
    def run(self, input_params: Dict, *args, **kwargs) -> Dict:
        """Runs the process to create a process definition for a Jasper report in the Etendo Application Dictionary."""
        prefix = input_params.get('i_prefix', "").upper()
        searchkey = input_params.get('i_searchkey', "").replace(" ", "_")
        if not searchkey.startswith(prefix):
            searchkey = f"{prefix}_{searchkey}"
        report_name = input_params.get('i_report_name', "")
        help_comment = input_params.get('i_help_comment', "")
        description = input_params.get('i_description', "")
        parameters_string = input_params.get('i_parameters', "")
        parameters = process_parameters(parameters_string)
        report_path = input_params.get('i_report_path', "")
        path_parts = report_path.split('/')
        web_index = path_parts.index('web')
        truncated_path = "/".join(path_parts[web_index:web_index + 4])

        webhook_name = "ProcessDefinitionJasper"
        body_params = {
            "Prefix": prefix,
            "SearchKey": searchkey,
            "ReportName": report_name,
            "HelpComment": help_comment,
            "Description": description,
            "Parameters": parameters,
            "ReportPath": truncated_path
        }

        post_result = call_webhook(get_etendo_token(), body_params, get_etendo_host(), webhook_name)

        return post_result
