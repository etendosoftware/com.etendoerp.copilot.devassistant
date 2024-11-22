import json
from typing import Dict, Type, Optional

import requests
from langsmith import traceable

from copilot.core import utils
from copilot.core.threadcontext import ThreadContext
from copilot.core.tool_input import ToolField, ToolInput
from copilot.core.tool_wrapper import ToolWrapper
from copilot.core.utils import copilot_debug


class CreateReferencesInput(ToolInput):
    i_prefix: str = ToolField(
        title="Prefix", description="This is the prefix of the module in the database."
    )

    i_name: str = ToolField(
        title="Name", description="This is the name of the reference."
    )

    i_reference_list: str = ToolField(
        title="Reference List", description="Comma-separated list of reference items."
    )

    i_help: Optional[str] = ToolField(
        title="Help", description="Help text for the reference."
    )

    i_description: Optional[str] = ToolField(
        title="Description", description="Description of the reference."
    )


@traceable
def _get_headers(access_token: Optional[str]) -> Dict[str, str]:
    headers = {}
    if access_token:
        headers["Authorization"] = f"Bearer {access_token}"
    return headers


@traceable
def call_webhook(
    access_token: Optional[str], body_params: Dict, url: str, webhook_name: str
) -> Dict:
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


class CreateReferencesTool(ToolWrapper):
    """This tool creates a list reference in the Etendo Application Dictionary."""

    name: str = "CreateReferencesTool"
    description: str = "Creates a list reference in the Etendo Application Dictionary."
    args_schema: Type[ToolInput] = CreateReferencesInput

    @traceable
    def run(self, input_params: Dict, *args, **kwargs) -> Dict:
        """Runs the process to create a reference in the Etendo Application Dictionary."""
        prefix = input_params.get("i_prefix", "").upper()
        name = input_params.get("i_name", "").replace(" ", "_")
        reference_list = input_params.get("i_reference_list", "")
        help_text = input_params.get("i_help", "")
        description = input_params.get("i_description", "")

        extra_info = ThreadContext.get_data("extra_info")
        if (
            not extra_info
            or not extra_info.get("auth")
            or not extra_info.get("auth").get("ETENDO_TOKEN")
        ):
            return {
                "error": "No access token provided. To work with Etendo, an access token is required."
                "Make sure that the Webservices are enabled for the user role and the WS are configured for"
                " the Entity."
            }

        access_token = extra_info.get("auth").get("ETENDO_TOKEN")
        etendo_host = utils.read_optional_env_var(
            "ETENDO_HOST", "http://host.docker.internal:8080/etendo"
        )
        copilot_debug(f"ETENDO_HOST: {etendo_host}")

        webhook_name = "CreateReference"
        body_params = {
            "Prefix": prefix,
            "NameReference": name,
            "ReferenceList": reference_list,
            "Help": help_text,
            "Description": description,
        }

        post_result = call_webhook(access_token, body_params, etendo_host, webhook_name)
        return post_result
