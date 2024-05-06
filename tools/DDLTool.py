import json
from typing import Dict, Type, Optional
from pydantic import BaseModel, Field
from copilot.core import utils
from copilot.core.threadcontext import ThreadContext
from copilot.core.tool_wrapper import ToolWrapper
from copilot.core.utils import copilot_debug

class DDLToolInput(BaseModel):
    i_mode: int = Field(
        title="Mode",
        description="This parameter indicates what want to do the user. The availables modes are:"
                    "REGISTER_TABLE: Register a table in the Etendo Application Dictionary to be recognized for it.",
        enum=['REGISTER_TABLE']
    )
    i_prefix: Optional[str]  = Field(
        title="Prefix",
        description="This is the prefix of the module in database. Only used for REGISTER_TABLE mode. "
    )
    i_name: Optional[str]  = Field(
        title="Name",
        description="This is the name of the table, this construct the database name adding the prefix before and a '_'. Only used for REGISTER_TABLE mode."
    )
    i_classname: Optional[str] = Field(
        None,
        title="ClassName",
        description="This is the java class name associated to the table, if this is not provided will be generated automatically. Only used for REGISTER_TABLE mode."
    )


def _get_headers(access_token: Optional[str]) -> Dict:
    """
    This method generates headers for an HTTP request.

    Parameters:
    access_token (str, optional): The access token to be included in the headers. If provided, an 'Authorization' field is added to the headers with the value 'Bearer {access_token}'.

    Returns:
    dict: A dictionary representing the headers. If an access token is provided, the dictionary includes an 'Authorization' field.
    """
    headers = {}

    if access_token:
        headers["Authorization"] = f"Bearer {access_token}"
    return headers


available_modes = ["REGISTER_TABLE"]


def register_table(url, acces_token, prefix, name, classname):
    import requests
    webhook_name = "RegisterTable"
    body_params = {
        "DBPrefix": prefix,  # a la izq es como esta registrado en etendo y a la derecha, como lo tengo al valor
        "JavaClass": classname,
        "Name": name

    }
    post_result = call_webhook(acces_token, body_params, url, webhook_name)
    return post_result


def call_webhook(access_token, body_params, url, webhook_name):
    import requests
    headers = _get_headers(access_token)
    endpoint = "/webhooks/?name=" + webhook_name
    import json
    json_data= json.dumps(body_params)
    post_result = requests.post(url=(url + endpoint), data=json_data, headers=headers)
    if post_result.ok:
         return json.loads(post_result.text)
    else:
        copilot_debug(post_result.text)
        return {"error":post_result.text}


class DDLTool(ToolWrapper):
    name = 'DDLTool'
    description = "This tool register a table on the AD_Table in Etendo."
    args_schema: Type[BaseModel] = DDLToolInput

    def run(self, input_params: Dict, *args, **kwargs):

        mode = input_params.get('i_mode')
        prefix = input_params.get('i_prefix')
        name = input_params.get('i_name')
        tablename: str = prefix.upper() + '_' + name
        classname: str = input_params.get('i_classname')

        extra_info = ThreadContext.get_data('extra_info')
        access_token = extra_info.get('auth').get('ETENDO_TOKEN')
        etendo_host = utils.read_optional_env_var("ETENDO_HOST", "http://host.docker.internal:8080/etendo")

        if classname is None:
            classname = tablename.replace("_", "")

        if mode == "REGISTER_TABLE":
            return register_table(etendo_host, access_token, prefix, name, classname)

        else:
            return {"error": "Wrong Mode. Available modes are " + str(available_modes)}

