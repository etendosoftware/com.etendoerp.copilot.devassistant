import json
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
    i_mode: int = Field(
        title="Mode",
        description="This parameter indicates what want to do the user. The available modes are:"
                    "REGISTER_TABLE: Register a table in the Etendo Application Dictionary to be recognized for it."
                    "CREATE_TABLE: Create a table on the database."
                    "ADD_COLUMN: This mode works adding a specific column to a created table.",
        enum=['REGISTER_TABLE', 'CREATE_TABLE', 'ADD_COLUMN']
    )
    i_prefix: Optional[str] = Field(
        title="Prefix",
        description="This is the prefix of the module in database. Only used for REGISTER_TABLE and CREATE_TABLE mode. "
    )
    i_name: Optional[str] = Field(
        title="Name",
        description="This is the name of the table, this construct the database name adding the prefix before "
                    "and a '_'. Only used for REGISTER_TABLE and CREATE_TABLE mode."
    )
    i_classname: Optional[str] = Field(
        None,
        title="ClassName",
        description="This is the java class name associated to the table, if this is not provided will be generated "
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
              "Price","Product Characteristics", "Quantity", "Rich Text Area", "RowID", "Search", "Search Vector",
              "String","Table","TableDir", "Text", "Time", "Transactional Sequence", "Tree Reference",
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
        enum=['Y', 'N']
    )


def _get_headers(access_token: Optional[str]) -> Dict:
    """
    This method generates headers for an HTTP request.

    Parameters:
    access_token (str, optional): The access token to be included in the headers. If provided, an 'Authorization' field
    is added to the headers with the value 'Bearer {access_token}'.

    Returns:
    dict: A dictionary representing the headers. If an access token is provided, the dictionary includes an '
    Authorization' field.
    """
    headers = {}

    if access_token:
        headers["Authorization"] = f"Bearer {access_token}"
    return headers


available_modes = ["REGISTER_TABLE", "CREATE_TABLE", "ADD_COLUMN"]


def register_table(url, access_token, prefix, name, classname):
    import requests
    if classname is None:
        classname = prefix + name

    webhook_name = "RegisterTable"
    body_params = {
        "DBPrefix": prefix,
        "JavaClass": classname,
        "Name": name
    }
    post_result = call_webhook(access_token, body_params, url, webhook_name)
    return post_result


def create_table(url, access_token, mode, prefix, name):
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

    query_constraint = " "
    default = ""
    query_null = " "

    if not can_be_null:
        query_null = " NOT NULL"

    if default_value is not None:
        default = f" DEFAULT '{default_value}'::bpchar"

    if dbtype == CHAR1:
        query_constraint = f", ADD CONSTRAINT {prefix}_{name}_{column}_chk CHECK ({column} = ANY (ARRAY['Y'::bpchar, 'N'::bpchar]))"

    query = f"""
            ALTER TABLE IF EXISTS public.{prefix}_{name}
                ADD COLUMN {column} {dbtype} COLLATE pg_catalog."default" {query_null} {default} {query_constraint};
            """

    webhook_name = "CreateTable"
    body_params = {
        "mode": mode,
        "query": query
    }
    post_result = call_webhook(access_token, body_params, etendo_host, webhook_name)
    return post_result
    #print(query)


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


class DDLTool(ToolWrapper):
    name = 'DDLTool'
    description = "This tool register a table on the AD_Table in Etendo."
    args_schema: Type[BaseModel] = DDLToolInput

    def run(self, input_params: Dict, *args, **kwargs):

        mode = input_params.get('i_mode')

        #TABLE DATA
        prefix = input_params.get('i_prefix')
        name = input_params.get('i_name')
        classname: str = input_params.get('i_classname')

        #VARIABLES ADD_COLUMN
        column: str = input_params.get('i_column')
        column_type: str = input_params.get('i_column_type')
        default_value: str = input_params.get('i_default_value')
        can_be_null: bool = input_params.get('i_can_be_null')

        #WEBHOOK DATA
        extra_info = ThreadContext.get_data('extra_info')
        access_token = extra_info.get('auth').get('ETENDO_TOKEN')
        etendo_host = utils.read_optional_env_var("ETENDO_HOST", "http://host.docker.internal:8080/etendo")

        if mode == "REGISTER_TABLE":
            return register_table(etendo_host, access_token, prefix, name, classname)
        elif mode == "CREATE_TABLE":
            return create_table(etendo_host, access_token, mode, prefix, name)
        elif mode == "ADD_COLUMN":
            return add_column(etendo_host, access_token, mode, prefix, name, column, column_type, default_value, can_be_null)
        else:
            return {"error": "Wrong Mode. Available modes are " + str(available_modes)}
