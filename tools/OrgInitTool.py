from typing import Dict, Optional, Type

from copilot.core import etendo_utils
from copilot.core.tool_input import ToolField, ToolInput
from copilot.core.tool_wrapper import (
    ToolOutput,
    ToolOutputError,
    ToolOutputMessage,
    ToolWrapper,
)
from tools.ClientInitTool import do_init_request


class OrgInitInput(ToolInput):
    org_name: str = ToolField(
        title="Organization Name",
        description="The name of the organization to be created.",
    )
    org_username: str = ToolField(
        title="Organization User",
        description="The username of the organization admin user to be created.",
    )
    password: str = ToolField(
        title="Password",
        description="The password of the organization admin user to be created.",
    )
    confirm_password: str = ToolField(
        title="Confirm Password",
        description="The password of the organization admin user to be created.",
    )
    client_admin_user: Optional[str] = ToolField(
        title="Client Admin User",
        description="The client admin user that wants to create the organization. If "
        "not provided, it will be inferred from the context.",
    )
    client_admin_password: Optional[str] = ToolField(
        title="Client Admin Password",
        description="The password of the client admin user that wants to create the "
        "organization. If not provided, it will"
        " be inferred from the context.",
    )
    remote_host: Optional[str] = ToolField(
        title="Remote Host",
        description="The remote host where the organization will be created. If not "
        "provided, it will the local host.",
    )


def initial_org_setup(
    bearer_token,
    inp_organization,
    inp_org_user,
    inp_password,
    inp_confirm_password,
    server_url,
):
    url = f"{server_url}/ad_forms/InitialOrgSetup.html?stateless=true"
    body = {
        "Command": "OK",
        "inpOrganization": inp_organization,
        "inpOrgUser": inp_org_user,
        "inpPassword": inp_password,
        "inpConfirmPassword": inp_confirm_password,
        "inpOrgType": "3",
        "inpParentOrg": "0",
        "inpTreeClass": "org.openbravo.erpCommon.modules.ModuleReferenceDataOrgTree",
        "inpNodeId": "7BFA8FF057AB46CAAB2FAAED8B870E32",
        "inpNodes": "7BFA8FF057AB46CAAB2FAAED8B870E32",
    }
    response = do_init_request(bearer_token, body, url)

    if response.status_code == 200:
        print("Initial organization setup completed successfully.")
        return ToolOutputMessage(
            message=f"Initial organization setup completed successfully. Organization "
            f"user credentials: "
            f"\n User: '{inp_org_user}' "
            f"\n Password: '{inp_password}'"
        )
    else:
        print(
            f"Error {response.status_code} in initial organization setup: "
            f"{response.text}"
        )
        return ToolOutputError(
            error=f"Error {response.status_code} in initial organization setup: "
            f"{response.text}"
        )


class OrgInitTool(ToolWrapper):
    name: str = "OrgInitTool"
    description: str = "A tool that initializes/creates a new organization in Etendo."
    args_schema: Type[ToolInput] = OrgInitInput

    def run(self, input_params: Dict = None, *args, **kwarg) -> ToolOutput:
        org_name = input_params.get("org_name")
        org_user = input_params.get("org_username")
        password = input_params.get("password")
        confirm_password = input_params.get("confirm_password")
        client_admin_user = input_params.get("client_admin_user")
        client_admin_password = input_params.get("client_admin_password")
        server_url = input_params.get("remote_host")
        if server_url is None:
            server_url = etendo_utils.get_etendo_host()
        if client_admin_user is None:
            token = etendo_utils.get_etendo_token()
        else:
            token = etendo_utils.login_etendo(
                server_url, client_admin_user, client_admin_password
            )
        if password != confirm_password:
            return ToolOutputError(error="Passwords do not match.")
        try:
            return initial_org_setup(
                bearer_token=token,
                inp_organization=org_name,
                inp_org_user=org_user,
                inp_password=password,
                inp_confirm_password=confirm_password,
                server_url=server_url,
            )
        except Exception as e:
            return ToolOutputError(error=str(e))
