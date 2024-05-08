import json
from typing import Dict, Type, Optional
from pydantic import BaseModel, Field
from copilot.core import utils
from copilot.core.threadcontext import ThreadContext
from copilot.core.tool_wrapper import ToolWrapper
from copilot.core.utils import copilot_debug


class DDLToolInput(BaseModel):
    i_mode: str = Field(
        title="Mode",
        description="This parameter indicates what want to do the user. The available modes are:"
                    "CREATE_TABLE: Create a table in the database with the provided prefix and name. It creates the "
                    "table with default columns(the ones that are always present in the tables of the Etendo Application)."
                    "REGISTER_TABLE: Register a table in the Etendo Application Dictionary to be recognized for it."
                    "REGISTER_COLUMNS: Register the columns of a table in the Etendo Application Dictionary, to be "
                    "recognized for it. It works incrementally, so if the table columns are already registered, it will"
                    " not be duplicated and the new columns will be added."
                    "SYNC_TERMINOLOGY: Synchronize the terminology of the Etendo Application Dictionary with the "
                    "correct names."
                    "REGISTER_WINDOW_AND_TAB: Register a window and tab in the Etendo Application Dictionary, to show "
                    "the table data in the application. This Mode requires the parameter Record ID to be provided(id of"
                    " the table, returned by the register table mode) and a name for the window and tab."
                    " It checks if there exists a window and tab with for the table, "
                    "if not, it creates it. If the window and tab already exists, it will not be duplicated. If it "
                    "neccesary, its possible to Force the creation of the window and tab with the parameter "
                    "ForceCreate."
                    "REGISTER_FIELDS: Register the fields of a tab in the Etendo Application Dictionary, "
                    "to be recognized for it."
                    "It works incrementally, so if the fields are already registered, it will not be duplicated and "
                    "the new fields will be added.",
        enum=['CREATE_TABLE', 'REGISTER_TABLE', 'REGISTER_COLUMNS', 'SYNC_TERMINOLOGY', 'REGISTER_WINDOW_AND_TAB',
              'REGISTER_FIELDS']
    )
    i_prefix: Optional[str] = Field(
        title="Prefix",
        description="This is the prefix of the module in database. Only used for CREATE_TABLE, REGISTER_TABLE and REGISTER_COLUMNS"
    )
    i_name: Optional[str] = Field(
        title="Name",
        description="This is the name of the table, this construct the database name adding the prefix "
                    "before a '_'."
                    " Only used for CREATE_TABLE, REGISTER_TABLE, REGISTER_COLUMNS and REGISTER_WINDOW_AND_TAB mode."
                    "In the mode CREATE_TABLE, this is the name of the table in the database."
                    "In the mode REGISTER_TABLE, this is the name of the table in the Etendo Application Dictionary."
                    "In the mode REGISTER_COLUMNS, this is the name of the table in the Etendo Application Dictionary."
                    "In the mode REGISTER_WINDOW_AND_TAB, this is the name of the table in the Etendo Application "
                    "Dictionary. For example, if the Table is PREFIX_Dogs, the name for the window and tab will be Dogs."
    )
    i_classname: Optional[str] = Field(
        None,
        title="ClassName",
        description="This is the java class name associated to the table, if this is not provided will be generated "
                    "automatically. Only used for REGISTER_TABLE mode."
    )
    i_record_id: Optional[str] = Field(
        title="Record ID",
        description="This is the record ID of the element to process in the Mode. "
                    "This ID is a string with 32 characters in Hexadecimal format. "
                    "Only used for REGISTER_FIELDS and REGISTER_WINDOW_AND_TAB mode."
                    "In the mode REGISTER_FIELDS, this is the ID of tab in the Application Dictionary (This id must be "
                    "returned by the REGISTER_WINDOW mode)."
                    "In the mode REGISTER_WINDOW_AND_TAB, this is the ID of the table in the Application Dictionary."
    )
    i_cleanTerminology: Optional[bool] = Field(
        title="Clean Terminology",
        description="This parameter indicates if the terminology must be cleaned before the synchronization. This do a "
                    "default modification of the terminology to remove the _ and add spaces. "
                    "Only used for SYNC_TERMINOLOGY mode."
    )
    i_forceCreate: Optional[bool] = Field(
        title="Force Create",
        description="This parameter indicates if the window and tab must be created even if it already exists. "
                    "Only used for REGISTER_WINDOW_AND_TAB mode. If not provided, the default value is False."
    )


def _get_headers(access_token: Optional[str]) -> Dict:
    """
    This method generates headers for an HTTP request.

    Parameters:
    access_token (str, optional): The access token to be included in the headers. If provided, an 'Authorization' field
     is added to the headers with the value 'Bearer {access_token}'.

    Returns:
    dict: A dictionary representing the headers. If an access token is provided, the dictionary includes an
     'Authorization' field.
    """
    headers = {}

    if access_token:
        headers["Authorization"] = f"Bearer {access_token}"
    return headers


available_modes = ["CREATE_TABLE", "REGISTER_TABLE", "REGISTER_COLUMNS", "SYNC_TERMINOLOGY", "REGISTER_WINDOW_AND_TAB",
                   "REGISTER_FIELDS"]


def register_table(url, access_token, prefix, name, classname):
    if classname is None:
        classname = prefix.upper() + name[0].upper() + name[1:]

    webhook_name = "RegisterTable"
    body_params = {
        "DBPrefix": prefix,
        "JavaClass": classname,
        "Name": name
    }
    post_result = call_webhook(access_token, body_params, url, webhook_name)
    return post_result


def create_table(url, access_token, prefix, name):
    query = f"""
                CREATE TABLE IF NOT EXISTS public.{prefix}_{name}
                (
                    {prefix}_{name}_id character varying(32) COLLATE pg_catalog."default" NOT NULL,
                    ad_client_id character varying(32) COLLATE pg_catalog."default" NOT NULL,
                    ad_org_id character varying(32) COLLATE pg_catalog."default" NOT NULL,
                    isactive character(1) COLLATE pg_catalog."default" NOT NULL DEFAULT 'Y'::bpchar,
                    created timestamp without time zone NOT NULL DEFAULT now(),
                    createdby character varying(32) COLLATE pg_catalog."default" NOT NULL,
                    updated timestamp without time zone NOT NULL DEFAULT now(),
                    updatedby character varying(32) COLLATE pg_catalog."default" NOT NULL,
                    CONSTRAINT {prefix}_{name}_pk PRIMARY KEY ({prefix}_{name}_id),
                    CONSTRAINT {prefix}_{name}_ad_client FOREIGN KEY (ad_client_id)
                        REFERENCES public.ad_client (ad_client_id) MATCH SIMPLE
                        ON UPDATE NO ACTION
                        ON DELETE NO ACTION,
                    CONSTRAINT {prefix}_{name}_ad_org FOREIGN KEY (ad_org_id)
                        REFERENCES public.ad_org (ad_org_id) MATCH SIMPLE
                        ON UPDATE NO ACTION
                        ON DELETE NO ACTION,
                    CONSTRAINT {prefix}_{name}_isactv_chk CHECK (isactive = ANY (ARRAY['Y'::bpchar,'N'::bpchar]))
                )

                TABLESPACE pg_default;

                ALTER TABLE IF EXISTS public.{prefix}_{name}
                    OWNER to tad;
        """

    webhook_name = "CreateTable"
    body_params = {
        "query": query
    }
    post_result = call_webhook(access_token, body_params, url, webhook_name)
    return post_result


def call_webhook(access_token, body_params, url, webhook_name):
    import requests
    headers = _get_headers(access_token)
    endpoint = "/webhooks/?name=" + webhook_name
    import json
    json_data = json.dumps(body_params)
    post_result = requests.post(url=(url + endpoint), data=json_data, headers=headers)
    if post_result.ok:
        return json.loads(post_result.text)
    else:
        copilot_debug(post_result.text)
        return {"error": post_result.text}


def register_columns(etendo_host, access_token, prefix, name):
    db_tablename: str = prefix.upper() + '_' + name
    webhook_name = "RegisterColumns"
    body_params = {
        "tableName": db_tablename
    }
    post_result = call_webhook(access_token, body_params, etendo_host, webhook_name)
    return post_result


def sync_terminoloy(etendo_host, access_token, clean_terminology):
    webhook_name = "SyncTerms"

    body_params = {}
    if clean_terminology:
        body_params = {
            "cleanTerminology": clean_terminology
        }
    post_result = call_webhook(access_token, body_params, etendo_host, webhook_name)
    return post_result


def register_fields(etendo_host, access_token, record_id):
    webhook_name = "RegisterFields"
    body_params = {
        "WindowTabID": record_id
    }
    post_result = call_webhook(access_token, body_params, etendo_host, webhook_name)
    return post_result


def register_window_and_tab(etendo_host, access_token, record_id, prefix, name, force_create):
    webhook_name = "RegisterWindowAndTab"
    if force_create is None:
        force_create = False
    body_params = {
        "TableID": record_id,
        "Name": name,
        "ForceCreate": force_create
    }
    post_result = call_webhook(access_token, body_params, etendo_host, webhook_name)
    return post_result


class DDLTool(ToolWrapper):
    name = 'DDLTool'
    description = "This tool register a table on the AD_Table in Etendo."
    args_schema: Type[BaseModel] = DDLToolInput

    def run(self, input_params: Dict, *args, **kwargs):
        # read the parameters
        mode = input_params.get('i_mode')
        prefix = input_params.get('i_prefix')
        name = input_params.get('i_name')
        classname: str = input_params.get('i_classname')
        record_id = input_params.get('i_record_id')
        clean_terminology = input_params.get('i_cleanTerminology')
        force_create = input_params.get('i_forceCreate')

        extra_info = ThreadContext.get_data('extra_info')
        access_token = extra_info.get('auth').get('ETENDO_TOKEN')
        etendo_host = utils.read_optional_env_var("ETENDO_HOST", "http://host.docker.internal:8080/etendo")

        if mode == "REGISTER_TABLE":
            return register_table(etendo_host, access_token, prefix, name, classname)
        elif mode == "REGISTER_COLUMNS":
            return register_columns(etendo_host, access_token, prefix, name)
        elif mode == "SYNC_TERMINOLOGY":
            return sync_terminoloy(etendo_host, access_token, clean_terminology)
        elif mode == "REGISTER_WINDOW_AND_TAB":
            return register_window_and_tab(etendo_host, access_token, record_id, prefix, name, force_create)
        elif mode == "REGISTER_FIELDS":
            return register_fields(etendo_host, access_token, record_id)
        elif mode == "CREATE_TABLE":
            return create_table(etendo_host, access_token, prefix, name)
        else:
            return {"error": "Wrong Mode. Available modes are " + str(available_modes)}
