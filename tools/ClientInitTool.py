from typing import Dict, Optional, Type

import requests

from copilot.core.etendo_utils import get_etendo_host, get_etendo_token, login_etendo
from copilot.core.tool_input import ToolField
from copilot.core.tool_input import ToolInput
from copilot.core.tool_wrapper import (
    ToolOutput,
    ToolOutputError,
    ToolOutputMessage,
    ToolWrapper,
)


def build_init_headers(bearer_token):
    return {
        "Authorization": f"Bearer {bearer_token}",
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,"
        "image/webp,image/apng,*/*;q=0.8,"
        "application/signed-exchange;v=b3;q=0.7",
    }


def initial_client_setup(
    bearer_token,
    inp_client,
    inp_password,
    inp_client_user,
    inp_confirm_password,
    inp_currency,
    server_url,
):
    url = f"{server_url}/ad_forms/InitialClientSetup.html?stateless=true"
    body = {
        "Command": "OK",
        "inpClient": inp_client,
        "inpPassword": inp_password,
        "inpClientUser": inp_client_user,
        "inpConfirmPassword": inp_confirm_password,
        "inpCurrency": inp_currency,
        "inpTreeClass": "org.openbravo.erpCommon.modules.ModuleReferenceDataClientTree",
        "inpNodeId": "0",
        "inpNodes": "0",
    }
    response = do_init_request(bearer_token, body, url)

    if response.status_code == 200:
        print("Initial client setup completed successfully.")
        return ToolOutputMessage(
            message=f"Initial client setup completed successfully. Client user "
            f"credentials: "
            f"\n User: '{inp_client_user}' "
            f"\n Password: '{inp_password}'"
        )
    else:
        print(f"Error {response.status_code} in initial client setup: {response.text}")
        return ToolOutputError(
            error=f"Error {response.status_code} in initial client "
            f"setup: {response.text}"
        )


def do_init_request(bearer_token, body, url):
    from requests_toolbelt.multipart.encoder import MultipartEncoder

    headers = build_init_headers(bearer_token)
    data = MultipartEncoder(fields=body)
    headers["Content-Type"] = data.content_type
    response = requests.post(url, headers=headers, data=data)
    return response


class ClientInitInput(ToolInput):
    client_name: str = ToolField(
        title="Client Name",
        description="The name of the client to be created.",
    )
    client_username: str = ToolField(
        title="Client User",
        description="The username of the client admin user.",
    )
    password: str = ToolField(
        title="Password",
        description="The password of the client admin user.",
    )
    confirm_password: str = ToolField(
        title="Confirm Password",
        description="The password of the client admin user.",
    )
    currency: str = ToolField(
        title="Currency",
        description="The currency of the client.",
    )
    sysadmin_user: Optional[str] = ToolField(
        title="SysAdmin User",
        description="The current user that wants to create the organization. If not "
        "provided, it will be inferred from the context.",
    )
    sysadmin_password: Optional[str] = ToolField(
        title="SysAdmin Password",
        description="The password of the current user that wants to create the "
        "organization. If not provided, it will be inferred from "
        "the context.",
    )
    remote_host: Optional[str] = ToolField(
        title="Remote Host",
        description="The remote host where the organization will be created. If not "
        "provided, it will the local host.",
    )


class ClientInitTool(ToolWrapper):
    name: str = "ClientInitTool"
    description: str = "A tool to initialize a client in the Etendo system."
    args_schema: Type[ToolInput] = ClientInitInput

    def run(self, input_params: Dict = None, *args, **kwarg) -> ToolOutput:
        client_name = input_params.get("client_name")
        client_user = input_params.get("client_username")
        password = input_params.get("password")
        confirm_password = input_params.get("confirm_password")
        currency = input_params.get("currency")
        server_url = input_params.get("remote_host")
        sysadmin_user = input_params.get("sysadmin_user")
        sysadmin_password = input_params.get("sysadmin_password")
        if server_url is None:
            server_url = get_etendo_host()
        if sysadmin_user is None:
            token = get_etendo_token()
        else:
            token = login_etendo(server_url, sysadmin_password, sysadmin_password)
        if password != confirm_password:
            return ToolOutputError(error="Passwords do not match.")
        try:
            return initial_client_setup(
                bearer_token=token,
                inp_client=client_name,
                inp_password=password,
                inp_client_user=client_user,
                inp_confirm_password=confirm_password,
                inp_currency=currency,
                server_url=server_url,
            )
        except Exception as e:
            return ToolOutputError(error=str(e))
