import json
from typing import Dict, Type, Optional, List

import requests
from langsmith import traceable

from copilot.core import utils
from copilot.core.threadcontext import ThreadContext
from copilot.core.tool_input import ToolField, ToolInput
from copilot.core.tool_wrapper import ToolWrapper
from copilot.core.utils import copilot_debug


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

    i_path: str = ToolField(
        title="Report Path",
        description="Path where the report is stored."
    )


@traceable
def _get_headers(access_token: Optional[str]) -> Dict[str, str]:
    headers = {}
    if access_token:
        headers["Authorization"] = f"Bearer {access_token}"
    return headers


@traceable
def call_webhook(access_token: Optional[str], body_params: Dict, url: str, webhook_name: str) -> Dict:
    headers = _get_headers(access_token)
    endpoint = f"/webhooks/?name={webhook_name}"
    json_data = json.dumps(body_params)
    full_url = f"{url}{endpoint}"

    copilot_debug(f"Calling Webhook(POST): {full_url}")
    post_result = requests.post(url=full_url, data=json_data, headers=headers)

    if post_result.ok:
        return post_result.json()
    else:
        copilot_debug(post_result.text)
        return {"error": post_result.text}


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
        path = input_params.get('i_path', "")
        path_parts = path.split('/')
        web_index = path_parts.index('web')
        truncated_path = "/".join(path_parts[web_index:web_index + 3])  #  "web/<MODULE_PACKAGE>/JasperReports/JasperFile.jrxml"

        extra_info = ThreadContext.get_data('extra_info')
        if not extra_info or not extra_info.get('auth') or not extra_info.get('auth').get('ETENDO_TOKEN'):
            return {"error": "No access token provided. To work with Etendo, an access token is required."
                             "Make sure that the Webservices are enabled for the user role and the WS are configured for"
                             " the Entity."}

        access_token = extra_info.get('auth').get('ETENDO_TOKEN')
        etendo_host = utils.read_optional_env_var("ETENDO_HOST", "http://host.docker.internal:8080/etendo")
        copilot_debug(f"ETENDO_HOST: {etendo_host}")

        webhook_name = "ProcessDefinitionJasper"
        body_params = {
            "Prefix": prefix,
            "SearchKey": searchkey,
            "ReportName": report_name,
            "HelpComment": help_comment,
            "Description": description,
            "Parameters": parameters,
            "Path": path
        }

        post_result = call_webhook(access_token, body_params, etendo_host, webhook_name)
        return post_result
