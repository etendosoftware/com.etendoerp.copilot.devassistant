import string
import random

from typing import Dict, Type, Optional

from pydantic import BaseModel, Field

from copilot.core import utils
from copilot.core.threadcontext import ThreadContext
from copilot.core.tool_wrapper import ToolWrapper
from copilot.core.utils import copilot_debug

CHAR1 = "character(1)"

VARCHAR60 = "character varying(60)"

VARCHAR32 = "character varying(32)"

TIMESTAMP_WITHOUT_TIMEZONE = "timestamp without time zone"


class DDLToolInput(BaseModel):
    i_mode: str = Field(
        title="Mode",
        description="This parameter indicates what want to do the user. The available modes are:"
                    "CREATE_TABLE: Create a table in the database with the provided prefix and name. It creates the "
                    "table with default columns(the ones that are always present in the tables of the Etendo Application)."
                    "ADD_COLUMN: This mode works adding a specific column to a created table."
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
                    "the new fields will be added."
                    "READ_ELEMENTS: Check if the record has a description and help comment.",
        enum=['CREATE_TABLE', 'ADD_COLUMN', 'REGISTER_TABLE', 'REGISTER_COLUMNS', 'SYNC_TERMINOLOGY',
              'REGISTER_WINDOW_AND_TAB', 'REGISTER_FIELDS', 'READ_ELEMENTS']

    )
    i_prefix: Optional[str] = Field(
        title="Prefix",
        description="This is the prefix of the module in database. Only used for CREATE_TABLE, REGISTER_TABLE and REGISTER_COLUMNS"
    )
    i_name: Optional[str] = Field(
        title="Name",
        description="This is the name of the table, this construct the database name adding the prefix "
                    "before a '_'."
                    "Only used for CREATE_TABLE, REGISTER_TABLE, REGISTER_COLUMNS and REGISTER_WINDOW_AND_TAB mode."
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
                    ""
                    "automatically. Only used for REGISTER_TABLE mode."
    )
    i_column: Optional[str] = Field(
        title="Column Name",
        description="This is the column name to be added to a created table. Only used for ADD_COLUMN mode."
    )
    i_column_type: Optional[str] = Field(
        title="Column Type",
        description="This is the type of column, it depends of what information the user wants to add to the column. "
                    "Only used for ADD_COLUMN mode.",
        enum=["Absolute DateTime", "Absolute Time", "Amount", "Assignment", "Binary", "Button", "Button List", "Color",
              "Date", "DateTime", "DateTime_From (Date)", "DateTime_To (Date)", "General Quantity", "ID", "Image",
              "Image BLOB", "Integer", "Link", "List", "Masked String", "Memo", "Non Transactional Sequence", "Number",
              "OBKMO_Widget in Form Reference", "OBUISEL_Multi Selector Reference", "OBUISEL_SelectorAsLink Reference",
              "OBUISEL_Selector Reference", "Password (decryptable)", "Password (not decryptable)", "PAttribute",
              "Price", "Product Characteristics", "Quantity", "Rich Text Area", "RowID", "Search", "Search Vector",
              "String", "Table", "TableDir", "Text", "Time", "Transactional Sequence", "Tree Reference",
              "Window Reference", "YesNo"]
    )
    i_can_be_null: Optional[bool] = Field(
        title="Can Be Null",
        description="This is a column attribute that signalize if the column can be null or not. Only used for "
                    "ADD_COLUMN mode."
    )
    i_default_value: Optional[str] = Field(
        None,
        title="Column Default Value",
        description="This is a default value for the column, this stay None if the user does not specific a default "
                    "value. Only used for ADD_COLUMN mode.",
        enum=["'Y'::bpchar", "'N'::bpchar", "now()", "0"]
    )
    i_description: str = Field(
        title="Description",
        description="This is a description of the information that contains the field. Is a space to write additional "
                    "related information. This can not be None, infer a description to add. "
                    "Only used for REGISTER_TABLE and REGISTER_WINDOW_AND_TAB and REGISTER_FIELDS mode."
    )
    i_help: str = Field(
        title="Help",
        description="This is a help for the complete these register. Is a short explanation of the content must has "
                    "the field. This can not be None, infer a help comment to add. "
                    "Only used for REGISTER_TABLE and REGISTER_WINDOW_AND_TAB and REGISTER_FIELDS mode."
    )
    i_data_access_level: str = Field(
        default="3",
        title="Data Access Level",
        description="This is the level for access to data, this is a number that represents the role can access to "
                    "data. Only used for REGISTER_TABLE mode.",
        enum=["1", "3", "4", "6", "7"]
    )
    i_record_id: Optional[str] = Field(
        title="Record ID",
        description="This is the record ID of the element to process in the Mode. "
                    "This ID is a string with 32 characters in Hexadecimal format. "
                    "Only used for REGISTER_FIELDS and REGISTER_WINDOW_AND_TAB mode."
                    "In the mode REGISTER_FIELDS, this is the ID of tab in the Application Dictionary (This id must be "
                    "returned by the REGISTER_WINDOW mode)."
                    "In the mode REGISTER_WINDOW_AND_TAB, this is the ID of the table in the Application Dictionary."
                    "In the mode READ_ELEMETNS, this is the ID of eache column in the created table."
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


available_modes = ["CREATE_TABLE", "ADD_COLUMN", "REGISTER_TABLE", "REGISTER_COLUMNS", "SYNC_TERMINOLOGY",
                   "REGISTER_WINDOW_AND_TAB", "REGISTER_FIELDS", "READ_ELEMENTS"]


def register_table(url, access_token, prefix, name, classname, dalevel, description, help_comment):

    if dalevel is None:
        dalevel = "3"

    webhook_name = "RegisterTable"
    body_params = {
        "DBPrefix": prefix,
        "JavaClass": classname,
        "Name": name,
        "DataAccessLevel": dalevel,
        "Description": description,
        "Help": help_comment
    }
    post_result = call_webhook(access_token, body_params, url, webhook_name)
    return post_result


def get_const_name(prefix, name1: str, name2: str, suffix):
    proposal = prefix + "_" + name1 + "_" + name2 + "_" + suffix
    if (len(proposal) > 32) and ("_" in name1 or "_" in name2):
        name1 = name1.replace("_", "")
        name2 = name2.replace("_", "")
    offset = 1
    while len(proposal) > 32 and offset < 15:
        name1offsetted = name1[offset:] if len(name1) > offset else name1
        name2offsetted = name2[offset:] if len(name2) > offset else name2
        proposal = (prefix + "_" + name1offsetted + "_" + name2offsetted + "_" + suffix)
        offset += 1
    if len(proposal) > 32:
        length = 32 - len(prefix) - len(suffix) - 2
        random_string = ''.join(random.choices(string.ascii_letters, k=length))
        proposal = prefix + "_" + random_string + "_" + suffix

    return proposal


def create_table(url, access_token, mode, prefix, name):
    const_isactive = get_const_name(prefix, name, 'isactive', 'chk')
    constr_pk = get_const_name(prefix, name, '', 'pk')
    constr_fk_client = get_const_name(prefix, name, 'ad_client', 'fk')
    constr_fk_org = get_const_name(prefix, name, 'ad_org', 'fk')

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
                    CONSTRAINT {constr_pk} PRIMARY KEY ({prefix}_{name}_id),
                    CONSTRAINT {constr_fk_client} FOREIGN KEY (ad_client_id)
                        REFERENCES public.ad_client (ad_client_id) MATCH SIMPLE
                        ON UPDATE NO ACTION
                        ON DELETE NO ACTION,
                    CONSTRAINT {constr_fk_org} FOREIGN KEY (ad_org_id)
                        REFERENCES public.ad_org (ad_org_id) MATCH SIMPLE
                        ON UPDATE NO ACTION
                        ON DELETE NO ACTION,
                    CONSTRAINT  {const_isactive} CHECK (isactive = ANY (ARRAY['Y'::bpchar,'N'::bpchar]))
                )

                TABLESPACE pg_default;

        """

    webhook_name = "CreateTable"
    body_params = {
        "mode": mode,
        "query": query
    }

    post_result = call_webhook(access_token, body_params, url, webhook_name)
    return post_result


def add_column(etendo_host, access_token, mode, prefix, name, column, type_name, default_value, can_be_null):
    mapping = {
        "Absolute DateTime": TIMESTAMP_WITHOUT_TIMEZONE,
        "Absolute Time": TIMESTAMP_WITHOUT_TIMEZONE,
        "Amount": "numeric",
        "Assignment": VARCHAR32,
        "Binary": "bytea",
        "Button": CHAR1,
        "Button List": VARCHAR60,
        "Color": VARCHAR60,
        "Date": TIMESTAMP_WITHOUT_TIMEZONE,
        "DateTime": TIMESTAMP_WITHOUT_TIMEZONE,
        "DateTime_From (Date)": TIMESTAMP_WITHOUT_TIMEZONE,
        "DateTime_To (Date)": TIMESTAMP_WITHOUT_TIMEZONE,
        "General Quantity": "numeric",
        "ID": VARCHAR32,
        "Image": VARCHAR60,
        "Image BLOB": VARCHAR32,
        "Integer": "numeric",
        "Link": "character varying(200)",
        "List": VARCHAR60,
        "Masked String": VARCHAR60,
        "Memo": "character varying(4000)",
        "Non Transactional Sequence": VARCHAR60,
        "Number": "numeric",
        "OBKMO_Widget in Form Reference": VARCHAR32,
        "OBUISEL_Multi Selector Reference": VARCHAR60,
        "OBUISEL_SelectorAsLink Reference": "numeric",
        "OBUISEL_Selector Reference": VARCHAR60,
        "Password (decryptable)": "character varying(255)",
        "Password (not decryptable)": "character varying(255)",
        "PAttribute": VARCHAR32,
        "Price": "numeric",
        "Product Characteristics": "character varying(2000)",
        "Quantity": "numeric",
        "Rich Text Area": "text",
        "RowID": VARCHAR60,
        "Search": VARCHAR32,
        "Search Vector": VARCHAR60,
        "String": "character varying(200)",
        "Table": VARCHAR32,
        "TableDir": VARCHAR32,
        "Text": "text",
        "Time": TIMESTAMP_WITHOUT_TIMEZONE,
        "Transactional Sequence": VARCHAR60,
        "Tree Reference": VARCHAR32,
        "Window Reference": VARCHAR60,
        "YesNo": CHAR1
    }

    dbtype = mapping[type_name]

    column = column.split(" ")
    column = "".join([word.capitalize() for word in column])

    query_collate = 'COLLATE pg_catalog."default"'
    query_null = " "
    default = ""
    query_constraint = " "

    if dbtype == TIMESTAMP_WITHOUT_TIMEZONE or dbtype == "numeric":
        query_collate = ""
        can_be_null = False

    if not can_be_null:
        query_null = " NOT NULL"

    if default_value is not None:
        default = f" DEFAULT {default_value}"

    if dbtype == CHAR1:
        query_constraint = f", ADD CONSTRAINT {prefix}_{name}_{column}_chk CHECK ({column} = ANY (ARRAY['Y'::bpchar, 'N'::bpchar]))"

    query = f"""
            ALTER TABLE IF EXISTS public.{prefix}_{name}
                ADD COLUMN {column} {dbtype} {query_collate} {query_null} {default} {query_constraint};
            """

    webhook_name = "CreateTable"
    body_params = {
        "mode": mode,
        "query": query
    }
    post_result = call_webhook(access_token, body_params, etendo_host, webhook_name)
    return post_result


def call_webhook(access_token, body_params, url, webhook_name):
    import requests
    headers = _get_headers(access_token)
    endpoint = "/webhooks/?name=" + webhook_name
    import json
    json_data = json.dumps(body_params)
    full_url = (url + endpoint)
    copilot_debug(f"Calling Webhook(POST): {full_url}")
    post_result = requests.post(url=full_url, data=json_data, headers=headers)
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


def register_fields(etendo_host, access_token, record_id, description, help_comment):
    webhook_name = "RegisterFields"
    body_params = {
        "WindowTabID": record_id,
        "Description": description,
        "Help/Comment": help_comment
    }
    post_result = call_webhook(access_token, body_params, etendo_host, webhook_name)
    return post_result


def register_window_and_tab(etendo_host, access_token, record_id, name, force_create, description, help_comment):
    fixed_name = name
    if "_" in name:
        fixed_name = name.replace("_", " ")
        fixed_name = fixed_name.split(" ")
        fixed_name = " ".join([word.capitalize() for word in fixed_name])

    webhook_name = "RegisterWindowAndTab"
    if force_create is None:
        force_create = False
    body_params = {
        "TableID": record_id,
        "Name": fixed_name,
        "ForceCreate": force_create,
        "Description": description,
        "Help/Comment": help_comment
    }
    post_result = call_webhook(access_token, body_params, etendo_host, webhook_name)
    return post_result


def read_elements(etendo_host, access_token, record_id):
    webhook_name = "ElementsHandler"
    body_params = {
        "TableID": record_id
    }
    post_result = call_webhook(access_token, body_params,etendo_host, webhook_name)
    return post_result


def write_elements(etendo_host, access_token, record_id, description):
    webhook_name = "ElementsHandler"
    body_params = {
        "ColumnId": record_id,
        "Description": description
    }
    post_result = call_webhook(access_token, body_params, etendo_host, webhook_name)
    return post_result


class DDLTool(ToolWrapper):
    name = 'DDLTool'
    description = ("This tool can register a table in Etendo, create tables on the data base and add specifics columns "
                   "to a new table or a created table, also can register columns and create window in Etendo.")
    args_schema: Type[BaseModel] = DDLToolInput

    def run(self, input_params: Dict, *args, **kwargs):
        # read the parameters
        mode = input_params.get('i_mode')

        # TABLE DATA
        prefix = input_params.get('i_prefix')
        if prefix is not None:
            prefix = prefix.upper()
        name = input_params.get('i_name')
        if name is not None:
            name = name.replace(" ", "_")

        # REGISTER VARIABLES
        classname: str = input_params.get('i_classname')
        dalevel: str = input_params.get('i_data_access_level')

        # ELEMENT VARIABLES
        description: str = input_params.get('i_description')
        help_comment: str = input_params.get('i_help')

        # ADD_COLUMN VARIABLES
        column: str = input_params.get('i_column')
        column_type: str = input_params.get('i_column_type')
        default_value: str = input_params.get('i_default_value')
        can_be_null: bool = input_params.get('i_can_be_null')

        # WEBHOOK DATA
        record_id = input_params.get('i_record_id')
        clean_terminology = input_params.get('i_cleanTerminology')
        force_create = input_params.get('i_forceCreate')

        extra_info = ThreadContext.get_data('extra_info')
        if extra_info is None or extra_info.get('auth') is None or extra_info.get('auth').get('ETENDO_TOKEN') is None:
            return {"error": "No access token provided, to work with Etendo, an access token is required."
                             "Make sure that the Webservices are enabled to the user role and the WS are configured for"
                             " the Entity."
                    }
        access_token = extra_info.get('auth').get('ETENDO_TOKEN')
        etendo_host = utils.read_optional_env_var("ETENDO_HOST", "http://host.docker.internal:8080/etendo")
        copilot_debug(f"ETENDO_HOST: {etendo_host}")

        if mode == "REGISTER_TABLE":
            return register_table(etendo_host, access_token, prefix, name, classname, dalevel, description,
                                  help_comment)
        elif mode == "REGISTER_COLUMNS":
            return register_columns(etendo_host, access_token, prefix, name)
        elif mode == "SYNC_TERMINOLOGY":
            return sync_terminoloy(etendo_host, access_token, clean_terminology)
        elif mode == "REGISTER_WINDOW_AND_TAB":
            return register_window_and_tab(etendo_host, access_token, record_id, name, force_create, description,
                                           help_comment)
        elif mode == "REGISTER_FIELDS":
            return register_fields(etendo_host, access_token, record_id, description, help_comment)
        elif mode == "CREATE_TABLE":
            return create_table(etendo_host, access_token, mode, prefix, name)
        elif mode == "ADD_COLUMN":
            return add_column(etendo_host, access_token, mode, prefix, name, column, column_type, default_value,
                              can_be_null)
        elif mode == "READ_ELEMENTS":
            return read_elements(etendo_host, access_token, record_id)
        elif mode == "WRITE_ELEMENTS":
            return write_elements(etendo_host, access_token, record_id, description)
        else:
            return {"error": "Wrong Mode. Available modes are " + str(available_modes)}
