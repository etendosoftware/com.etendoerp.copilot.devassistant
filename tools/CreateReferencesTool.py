import requests
import json
from typing import Dict, Type, Optional
from pydantic import BaseModel, Field
from copilot.core import utils
from copilot.core.threadcontext import ThreadContext
from copilot.core.tool_wrapper import ToolWrapper
from copilot.core.utils import copilot_debug

class CreateReferencesInput(BaseModel):
    i_prefix: str = Field(
        title="Prefix",
        description="This is the prefix of the module in the database."
    )

    i_name: str = Field(
        title="Name",
        description="This is the name of the reference."
    )

    i_reference_list: str = Field(
        title="Reference List",
        description="Comma-separated list of reference items."
    )

def _get_headers(access_token: Optional[str]) -> Dict[str, str]:
    headers = {}
    if access_token:
        headers["Authorization"] = f"Bearer {access_token}"
    return headers

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

class CreateReferences(ToolWrapper):
    """This tool creates a list reference in the Etendo Application Dictionary."""
    name = 'CreateReferences'
    description = "Creates a list reference in the Etendo Application Dictionary."
    args_schema: Type[BaseModel] = CreateReferencesInput

    def run(self, input_params: Dict, *args, **kwargs) -> Dict:
        """Runs the process to create a reference in the Etendo Application Dictionary."""
        prefix = input_params.get('i_prefix', "").upper()
        name = input_params.get('i_name', "").replace(" ", "_")
        reference_list = input_params.get('i_reference_list', "")

        extra_info = ThreadContext.get_data('extra_info')
        if not extra_info or not extra_info.get('auth') or not extra_info.get('auth').get('ETENDO_TOKEN'):
            return {"error": "No access token provided. To work with Etendo, an access token is required."
                             "Make sure that the Webservices are enabled for the user role and the WS are configured for"
                             " the Entity."}

        access_token = extra_info.get('auth').get('ETENDO_TOKEN')
        etendo_host = utils.read_optional_env_var("ETENDO_HOST", "https://host.docker.internal:8080/etendo")

        if not etendo_host.startswith("https://"):
            raise ValueError("The ETENDO_HOST must use HTTPS protocol for secure communication.")

        copilot_debug(f"ETENDO_HOST: {etendo_host}")

        webhook_name = "CreateReference"
        body_params = {
            "prefix": prefix,
            "nameReference": name,
            "referenceList": reference_list
        }

        post_result = call_webhook(access_token, body_params, etendo_host, webhook_name)
        return post_result