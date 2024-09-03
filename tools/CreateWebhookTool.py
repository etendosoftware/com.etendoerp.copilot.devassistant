from typing import Dict, Type, Optional

from langsmith import traceable

from copilot.core.etendo_utils import call_webhook, get_etendo_token, get_etendo_host
from copilot.core.tool_input import ToolField, ToolInput
from copilot.core.tool_wrapper import ToolWrapper, ToolOutput, ToolOutputError
from copilot.core.utils import copilot_debug


class CreateWebhookInput(ToolInput):
    i_prefix: str = ToolField(
        title="Prefix",
        description="This is the prefix of the module in the database."
    )
    i_searchkey: str = ToolField(
        title="Search Key",
        description="This is the search key that identifies the Webhook in the system."
    )
    i_javaclass: str = ToolField(
        title="Java Class",
        description="This is the Java class that handle the Webhook."
    )
    i_params: str = ToolField(
        title="Parameters",
        description="These are the parameters that the Webhook will receive. The parameters must be separated by "
                    "semicolons. Example: param1;param2;param3. All the parameters must be registered as mandatory in "
                    "the system. Spaces are not allowed."
    )


@traceable
def _get_headers(access_token: Optional[str]) -> Dict[str, str]:
    headers = {}
    if access_token:
        headers["Authorization"] = f"Bearer {access_token}"
    return headers


class CreateWebhookTool(ToolWrapper):
    """This tool creates a list reference in the Etendo Application Dictionary."""
    name = 'CreateWebhookTool'
    description = "Registers a new Webhook in the Etendo Application Dictionary."
    args_schema: Type[ToolInput] = CreateWebhookInput

    @traceable
    def run(self, input_params: Dict, *args, **kwargs) -> ToolOutput:
        """Runs the process to create a reference in the Etendo Application Dictionary."""
        prefix = input_params.get('i_prefix')
        searchkey = input_params.get('i_searchkey')
        javaclass = input_params.get('i_javaclass')
        params = input_params.get('i_params')
        if not prefix or not searchkey or not javaclass or not params:
            return ToolOutputError(error="All fields are required.")
        if ' ' in params:
            return ToolOutputError(error="Spaces are not allowed in the parameters.")

        access_token = get_etendo_token()
        etendo_host = get_etendo_host()
        copilot_debug(f"ETENDO_HOST: {etendo_host}")

        webhook_name = "CreateWebhook"
        body_params = {
            "prefix": prefix,
            "searchkey": searchkey,
            "javaclass": javaclass,
            "params": params
        }

        post_result = call_webhook(access_token, body_params, etendo_host, webhook_name)
        return post_result
