import random
import string
from typing import Dict, Type, Optional

from langsmith import traceable

from copilot.core import utils
from copilot.core.threadcontext import ThreadContext
from copilot.core.tool_input import ToolField, ToolInput
from copilot.core.tool_wrapper import ToolWrapper
from copilot.core.utils import copilot_debug

CHAR1 = "character(1)"

VARCHAR60 = "character varying(60)"

VARCHAR32 = "character varying(32)"

TIMESTAMP_WITHOUT_TIMEZONE = "timestamp without time zone"

MAX_LENGTH = 30


class DDLToolInput(ToolInput):
    i_mode: str = ToolField(
        title="Mode",
        description="This parameter indicates what want to do the user. The available modes are: "
        "CREATE_TABLE: Create a table in the database with the provided prefix and name. It creates the "
        "table with default columns (the ones that are always present in the tables of the Etendo "
        "Application)."
        "ADD_COLUMN: This mode works adding a specific column to a created table."
        "REGISTER_TABLE: Register a table in the Etendo Application Dictionary to be recognized for it."
        "REGISTER_COLUMNS: Register the columns of a table in the Etendo Application Dictionary, to be "
        "recognized for it. It works incrementally, so if the table columns are already registered, it will"
        " not be duplicated and the new columns will be added."
        "SYNC_TERMINOLOGY: Synchronize the terminology of the Etendo Application Dictionary with the "
        "correct names."
        "REGISTER_WINDOW: Register a window and tab in the Etendo Application Dictionary, to show "
        "the table data in the application. This Mode requires the parameter prefix and name."
        "It checks if there exists a window with the name for the table, if not, it creates it. If the "
        "window already exists, it will not be duplicated."
        "REGISTER_TAB: Register a tab on a window previously created, this mode need the window_id provided"
        "for the RegisterWindow mode, and the RecordId that is the table ID will be added."
        "REGISTER_FIELDS: Register the fields of a tab in the Etendo Application Dictionary, "
        "to be recognized for it."
        "It works incrementally, so if the fields are already registered, it will not be duplicated and "
        "the new fields will be added."
        "READ_ELEMENTS: Check if the record has a description and help comment."
        "WRITE_ELEMENTS: Set the description and help comment in the columns that do not have."
        "ADD_FOREIGN: This mode is useful to create a foreign key between two tables, a parent table and a "
        "child table.",
        enum=[
            "CREATE_TABLE",
            "ADD_COLUMN",
            "REGISTER_TABLE",
            "REGISTER_COLUMNS",
            "SYNC_TERMINOLOGY",
            "REGISTER_WINDOW",
            "REGISTER_TAB",
            "REGISTER_FIELDS",
            "READ_ELEMENTS",
            "WRITE_ELEMENTS",
            "ADD_FOREIGN",
            "GET_CONTEXT",
        ],
    )
    i_prefix: Optional[str] = ToolField(
        title="Prefix",
        description="This is the prefix of the module in database. Only used for CREATE_TABLE, REGISTER_TABLE and "
        "REGISTER_COLUMNS",
    )
    i_name: Optional[str] = ToolField(
        title="Name",
        description="This is the name of the table, this construct the database name adding the prefix "
        "before a '_'."
        "Only used for CREATE_TABLE, REGISTER_TABLE, REGISTER_COLUMNS and REGISTER_WINDOW mode."
        "In the mode CREATE_TABLE, this is the name of the table in the database."
        "In the mode REGISTER_TABLE, this is the name of the table in the Etendo Application Dictionary."
        "In the mode REGISTER_COLUMNS, this is the name of the table in the Etendo Application Dictionary."
        "In the mode REGISTER_WINDOW, this is the name of the table in the Etendo Application "
        "Dictionary. For example, if the Table is PREFIX_Dog, the name for the window and tab will be "
        "Dog.",
    )
    i_classname: Optional[str] = ToolField(
        None,
        title="ClassName",
        description="This is the java class name associated to the table, if this is not provided will be generated "
        ""
        "automatically. Only used for REGISTER_TABLE mode.",
    )
    i_column: Optional[str] = ToolField(
        title="Column Name",
        description="This is the column name to be added to a created table. Only used for ADD_COLUMN mode.",
    )
    i_column_type: Optional[str] = ToolField(
        title="Column Type",
        description="This is the type of column, it depends of what information the user wants to add to the column. "
        "Only used for ADD_COLUMN mode.",
        enum=[
            "Absolute DateTime",
            "Absolute Time",
            "Amount",
            "Assignment",
            "Binary",
            "Button",
            "Button List",
            "Color",
            "Date",
            "DateTime",
            "DateTime_From (Date)",
            "DateTime_To (Date)",
            "General Quantity",
            "ID",
            "Image",
            "Image BLOB",
            "Integer",
            "Link",
            "List",
            "Masked String",
            "Memo",
            "Non Transactional Sequence",
            "Number",
            "OBKMO_Widget in Form Reference",
            "OBUISEL_Multi Selector Reference",
            "OBUISEL_SelectorAsLink Reference",
            "OBUISEL_Selector Reference",
            "Password (decryptable)",
            "Password (not decryptable)",
            "PAttribute",
            "Price",
            "Product Characteristics",
            "Quantity",
            "Rich Text Area",
            "RowID",
            "Search",
            "Search Vector",
            "String",
            "Table",
            "TableDir",
            "Text",
            "Time",
            "Transactional Sequence",
            "Tree Reference",
            "Window Reference",
            "YesNo",
        ],
    )
    i_can_be_null: Optional[bool] = ToolField(
        title="Can Be Null",
        description="This is a column attribute that signalize if the column can be null or not. Only used for "
        "ADD_COLUMN mode.",
    )
    i_default_value: Optional[str] = ToolField(
        None,
        title="Column Default Value",
        description="This is a default value for the column, this stay None if the user does not specific a default "
        "value. Only used for ADD_COLUMN mode.",
        enum=["'Y'::bpchar", "'N'::bpchar", "now()", "0"],
    )
    i_description: str = ToolField(
        title="Description",
        description="It is an explanation, in a detailed and orderly manner. Description serves primarily to set the "
        "scene and explain the meaning of the information contained in the field.This is a description of "
        "the information that contains the field. Is a space to write additional related information. "
        "This can not be None, infer a description to add. "
        "Only used for REGISTER_TABLE, REGISTER_WINDOW, REGISTER_TAB and REGISTER_FIELDS mode.",
    )
    i_help: str = ToolField(
        title="Help",
        description="This field provides a more detailed and contextual explanation of the use and purpose of the "
        "field in question. The description offers a broad overview of the meaning and importance of the "
        "field. It may include details on why certain information is collected, how it will be used, and "
        "any relevant additional considerations. It is a short explanation of the content the field must "
        "have. This cannot be None; infer a help comment to add. Only used for REGISTER_TABLE, "
        "REGISTER_WINDOW, REGISTER_TAB and REGISTER_FIELDS mode.",
    )
    i_data_access_level: str = ToolField(
        default="3",
        title="Data Access Level",
        description="This is the level for access to data, this is a number that represents the role can access to "
        "data. Only used for REGISTER_TABLE mode.",
        enum=["1", "3", "4", "6", "7"],
    )
    i_record_id: Optional[str] = ToolField(
        title="Record ID",
        description="This is the record ID of the element to process in the Mode. "
        "This ID is a string with 32 characters in Hexadecimal format. "
        "Only used for REGISTER_FIELDS, REGISTER_WINDOW, READ_ELEMENTS and WRITE_ELEMENTS."
        "In the mode REGISTER_FIELDS, this is the ID of tab in the Application Dictionary (This id must be "
        "returned by the REGISTER_WINDOW mode)."
        "In the mode REGISTER_WINDOW, this is the ID of the table in the Application Dictionary."
        "In the mode READ_ELEMENTS, this is the ID of the created table where the columns will be checked."
        "In the mode WRITE_ELEMENTS, this is the ID of each column where the description and help comment "
        "will be added."
        "In the mode REGISTER_TAB, this is the ID of the table will associated to the tab.",
    )
    i_clean_terminology: Optional[bool] = ToolField(
        title="Clean Terminology",
        description="This parameter indicates if the terminology must be cleaned before the synchronization. This do a "
        "default modification of the terminology to remove the _ and add spaces. "
        "Only used for SYNC_TERMINOLOGY mode.",
    )

    i_tabLevel: Optional[str] = ToolField(
        title="Tab Level",
        description="This parameter indicates the tab level in the structure, the main table has the tab level = 0. "
        "The rest of the tabs has bigger     levels, if a tab must be 'inside' other has a next tab level "
        "(a tab with tab level 3 is inside other tab with tab level 2)."
        "This parameter must not be null or None."
        "Only used on REGISTER_TAB mode.",
        enum=[
            "0",
            "1",
            "2",
            "3",
            "4",
            "5",
            "6",
            "7",
            "8",
            "9",
            "10",
            "11",
            "12",
            "13",
            "14",
            "15",
        ],
    )
    i_parent_table: Optional[str] = ToolField(
        title="Parent Table",
        description="This parameter is the name in database (its own prefix and name) of the parent table of a foreign "
        "key. When a foreign key is created point from a parent table to the id of a child table."
        "Only used on ADD_FOREIGN mode.",
    )
    i_child_table: Optional[str] = ToolField(
        title="Child Table",
        description="This parameter is the name in database (its own prefix and name) of the child table of a foreign "
        "key. When a foreign key is created point from a parent table to the id of a child table."
        "Only used on ADD_FOREIGN mode.",
    )
    i_parent_column: Optional[str] = ToolField(
        title="Parent Column",
        description="is defined as the name assigned to the column in the parent table that acts as a foreign key, "
        "establishing a relationship with the primary key of the child table.",
    )
    i_window_id: Optional[str] = ToolField(
        title="WindowID",
        description="This parameter is the id of the window previously created, is obtained in the REGISTER_WINDOW"
        "mode. This is used when a tab is registered. Only used on REGISTER_TAB mode.",
    )
    i_sequence_number: Optional[str] = ToolField(
        title="Sequence Number",
        description="This parameter indicates the tab sequence number, with a smaller number indicating that it is "
        "displayed further to the left.",
    )
    i_key_word: Optional[str] = ToolField(
        title="Key Word",
        description="This parameter indicates the element that is searched. Only used on GET_CONTEXT mode.",
        enum=["table", "window", "tab"],
    )


@traceable
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


available_modes = [
    "CREATE_TABLE",
    "ADD_COLUMN",
    "REGISTER_TABLE",
    "REGISTER_COLUMNS",
    "SYNC_TERMINOLOGY",
    "REGISTER_WINDOW",
    "REGISTER_TAB",
    "REGISTER_FIELDS",
    "READ_ELEMENTS",
    "WRITE_ELEMENTS",
    "ADD_FOREIGN",
    "GET_CONTEXT",
]


@traceable
def register_table(
    url, access_token, prefix, name, classname, dalevel, description, help_comment
):
    if dalevel is None:
        dalevel = "3"

    webhook_name = "RegisterTable"
    body_params = {
        "DBPrefix": prefix,
        "JavaClass": classname,
        "Name": name,
        "DataAccessLevel": dalevel,
        "Description": description,
        "Help": help_comment,
    }
    post_result = call_webhook(access_token, body_params, url, webhook_name)
    return post_result


@traceable
def get_const_name(prefix, name1: str, name2: str, suffix):
    # "name1" and "name2" are the names of the tables involved in the relationship of the constraint.
    if name1.startswith(prefix + "_") or (name1.upper()).startswith(
        prefix.upper() + "_"
    ):
        name1 = name1[len(prefix) + 1 :]
    if name2.startswith(prefix + "_") or (name2.upper()).startswith(
        prefix.upper() + "_"
    ):
        name2 = name2[len(prefix) + 1 :]

    proposal = prefix + "_" + name1 + "_" + name2 + "_" + suffix
    if (len(proposal) > MAX_LENGTH) and ("_" in name1 or "_" in name2):
        name1 = name1.replace("_", "")
        name2 = name2.replace("_", "")
    offset = 1
    while len(proposal) > MAX_LENGTH and offset < 15:
        name1offsetted = name1[offset:] if len(name1) > offset else name1
        name2offsetted = name2[offset:] if len(name2) > offset else name2
        proposal = prefix + "_" + name1offsetted + "_" + name2offsetted + "_" + suffix
        offset += 1
    if len(proposal) > MAX_LENGTH:
        length = MAX_LENGTH - len(prefix) - len(suffix) - 2
        random_string = "".join(random.choices(string.ascii_letters, k=length))
        proposal = prefix + "_" + random_string + "_" + suffix

    proposal = proposal.replace("__", "_")

    return proposal


@traceable
def create_table(url, access_token, mode, prefix, name):
    const_isactive = get_const_name(prefix, name, "isactive", "chk")
    constr_pk = get_const_name(prefix, name, "", "pk")
    constr_fk_client = get_const_name(prefix, name, "ad_client", "fk")
    constr_fk_org = get_const_name(prefix, name, "ad_org", "fk")

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

    webhook_name = "DDLHook"
    body_params = {"Mode": mode, "Name": name, "Query": query}

    post_result = call_webhook(access_token, body_params, url, webhook_name)
    return post_result


@traceable
def add_column(
    etendo_host,
    access_token,
    mode,
    prefix,
    name,
    column,
    type_name,
    default_value,
    can_be_null,
):
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
        "YesNo": CHAR1,
    }

    dbtype = mapping[type_name]

    column = column.split(" ")
    column = "_".join([word.lower() for word in column])

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
        proposal = prefix + "_" + name + "_" + column + "_chk"
        column_offsetted = column
        offset = 1
        while len(proposal) > MAX_LENGTH and offset < 15:
            name_offsetted = name[offset:] if len(name) > offset else name
            column_offsetted = column[offset:] if len(column) > offset else column
            proposal = prefix + "_" + name_offsetted + "_" + column_offsetted + "_chk"
            offset += 1
        query_constraint = f", ADD CONSTRAINT {proposal}_chk CHECK ({column_offsetted} = ANY (ARRAY['Y'::bpchar, 'N'::bpchar]))"

    query = f"""
            ALTER TABLE IF EXISTS public.{prefix}_{name}
                ADD COLUMN IF NOT EXISTS {column} {dbtype} {query_collate} {query_null} {default} {query_constraint};
            """

    webhook_name = "DDLHook"
    body_params = {"Mode": mode, "Name": column, "Query": query}
    post_result = call_webhook(access_token, body_params, etendo_host, webhook_name)
    return post_result


@traceable
def call_webhook(access_token, body_params, url, webhook_name):
    import requests

    headers = _get_headers(access_token)
    endpoint = "/webhooks/?name=" + webhook_name
    import json

    json_data = json.dumps(body_params)
    full_url = url + endpoint
    copilot_debug(f"Calling Webhook(POST): {full_url}")
    post_result = requests.post(url=full_url, data=json_data, headers=headers)
    if post_result.ok:
        return json.loads(post_result.text)
    else:
        copilot_debug(post_result.text)
        return {"error": post_result.text}


@traceable
def register_columns(etendo_host, access_token, prefix, name):
    db_tablename: str = prefix.upper() + "_" + name
    webhook_name = "RegisterColumns"
    body_params = {"TableName": db_tablename}
    post_result = call_webhook(access_token, body_params, etendo_host, webhook_name)
    return post_result


@traceable
def sync_terminoloy(etendo_host, access_token, clean_terminology):
    webhook_name = "SyncTerms"

    body_params = {}
    if clean_terminology:
        body_params = {"CleanTerminology": clean_terminology}
    post_result = call_webhook(access_token, body_params, etendo_host, webhook_name)
    return post_result


@traceable
def register_fields(etendo_host, access_token, record_id, description, help_comment):
    webhook_name = "RegisterFields"
    body_params = {
        "WindowTabID": record_id,
        "Description": description,
        "HelpComment": help_comment,
    }
    post_result = call_webhook(access_token, body_params, etendo_host, webhook_name)
    return post_result


@traceable
def register_window(etendo_host, access_token, prefix, name, description, help_comment):
    fixed_name = name
    if "_" in name:
        fixed_name = name.replace("_", " ")
        fixed_name = fixed_name.split(" ")
        fixed_name = " ".join([word.capitalize() for word in fixed_name])

    webhook_name = "RegisterWindow"

    body_params = {
        "DBPrefix": prefix,
        "Name": fixed_name,
        "Description": description,
        "HelpComment": help_comment,
    }

    post_result = call_webhook(access_token, body_params, etendo_host, webhook_name)
    return post_result


@traceable
def read_elements(etendo_host, access_token, mode, record_id):
    webhook_name = "ElementsHandler"
    body_params = {"Mode": mode, "TableID": record_id}
    post_result = call_webhook(access_token, body_params, etendo_host, webhook_name)
    return post_result


@traceable
def write_elements(
    etendo_host, access_token, mode, record_id, description, help_comment
):
    webhook_name = "ElementsHandler"
    body_params = {
        "Mode": mode,
        "ColumnId": record_id,
        "Description": description,
        "HelpComment": help_comment,
    }
    post_result = call_webhook(access_token, body_params, etendo_host, webhook_name)
    return post_result


@traceable
def add_foreign(
    etendo_host, access_token, mode, prefix, parent_table, child_table, external
):
    if prefix is None or prefix == "None":
        prefix = parent_table.split("_")[0]

    prefix = prefix.lower()
    parent_table = parent_table.lower()
    child_table = child_table.lower()

    if child_table.startswith(prefix + "_"):
        child_table = child_table[len(prefix) + 1 :]

    parent_table_id = parent_table + "_id"
    parent_column = parent_table_id

    if external or not parent_table.startswith(prefix + "_"):
        parent_column = "em_" + parent_column
        prefix = "em_" + prefix

    add_column(
        etendo_host,
        access_token,
        "ADD_COLUMN",
        prefix,
        child_table,
        parent_column,
        "ID",
        None,
        False,
    )

    constraint_fk = get_const_name(prefix, child_table, parent_table, "fk")
    register_columns(etendo_host, access_token, prefix, child_table)

    query = f"""
            DO $$
            DECLARE
                foreign_key_exists BOOLEAN;
            BEGIN
                SELECT EXISTS (
                    SELECT 1
                    FROM pg_constraint c
                    JOIN pg_class t ON c.conrelid = t.oid
                    JOIN pg_namespace n ON t.relnamespace = n.oid
                    JOIN pg_class r ON c.confrelid = r.oid
                    WHERE c.contype = 'f'
                    AND t.relname = '{prefix}_{child_table}'
                    AND r.relname = '{parent_table}'
                    AND c.conkey = ARRAY(SELECT attnum
                                         FROM pg_attribute
                                         WHERE attrelid = t.oid
                                         AND attname = '{parent_column}')
                    AND c.confkey = ARRAY(SELECT attnum
                                          FROM pg_attribute
                                          WHERE attrelid = r.oid
                                          AND attname = '{parent_table_id}')
                ) INTO foreign_key_exists;

                IF foreign_key_exists THEN
                    UPDATE ad_column
                    SET isparent = 'Y'
                    WHERE name ILIKE '{parent_column}'
                    AND isupdateable = 'Y';
                ELSE
                    UPDATE ad_column
                    SET isparent = 'Y'
                    WHERE name ILIKE '{parent_column}'
                    AND isupdateable = 'Y';

                    ALTER TABLE IF EXISTS public.{prefix}_{child_table}
                    ADD CONSTRAINT {constraint_fk} FOREIGN KEY ({parent_column})
                    REFERENCES public.{parent_table} ({parent_table_id}) MATCH SIMPLE
                    ON UPDATE NO ACTION
                    ON DELETE NO ACTION;
                END IF;
            END $$;

            """

    webhook_name = "DDLHook"
    body_params = {"Mode": mode, "Query": query, "Name": constraint_fk}

    post_result = call_webhook(access_token, body_params, etendo_host, webhook_name)
    return post_result


@traceable
def register_tab(
    etendo_host,
    access_token,
    window_id,
    tab_level,
    description,
    help_comment,
    record_id,
    sequence_number,
):
    webhook_name = "RegisterTab"
    body_params = {
        "WindowID": window_id,
        "TabLevel": tab_level,
        "Description": description,
        "HelpComment": help_comment,
        "TableID": record_id,
        "SequenceNumber": sequence_number,
    }

    post_result = call_webhook(access_token, body_params, etendo_host, webhook_name)
    return post_result


@traceable
def get_context(etendo_host, access_token, mode, name, key_word):
    allowed_keywords = ["table", "window", "tab"]
    if key_word not in allowed_keywords:
        return {"error": f"Invalid key_word. Allowed values are {allowed_keywords}"}

    query = f"SELECT ad_{key_word}_id FROM ad_{key_word} WHERE name ilike '%{name}%'"

    key_word = key_word.upper()
    webhook_name = "DDLHook"
    body_params = {"Mode": mode, "Query": query, "Element": key_word, "Name": name}

    post_result = call_webhook(access_token, body_params, etendo_host, webhook_name)
    return post_result


def validate_extra_info():
    extra_info = ThreadContext.get_data("extra_info")
    if (
        extra_info is None
        or extra_info.get("auth") is None
        or extra_info.get("auth").get("ETENDO_TOKEN") is None
    ):
        return {
            "error": "No access token provided, to work with Etendo, an access token is required."
            "Make sure that the Webservices are enabled to the user role and the WS are configured for"
            " the Entity."
        }
    return extra_info.get("auth").get("ETENDO_TOKEN")


class DDLTool(ToolWrapper):
    name: str = "DDLTool"
    description: str = (
        "This tool can register tables, windows, and tabs in Etendo, create tables in the database, and"
        "add specific columns to a new or existing table. It can also register columns and create "
        "windows in Etendo. The DDLTool is a versatile tool designed to facilitate the creation and "
        "management of tables, columns, and windows within a system. Its functionality is based on a "
        "series of operation modes, each intended to perform specific tasks within the database "
        "development process. These modes guide the user through key steps, from the initial "
        "registration of tables to terminology synchronization and detailed field and element "
        "management. The available modes in the DDLTool range from table registration to terminology "
        "synchronization and the addition of foreign keys. For each mode, clear instructions and "
        "options are provided to define essential parameters, such as table names, column descriptions,"
        "and tab levels. Additionally, the tool offers the flexibility to automatically generate "
        "certain values if the user does not provide them."
    )
    args_schema: Type[ToolInput] = DDLToolInput

    @traceable
    def run(self, input_params: dict, *args, **kwargs):
        # read the parameters
        mode = input_params.get("i_mode")

        # TABLE DATA
        prefix = input_params.get("i_prefix")
        if prefix is not None:
            prefix = prefix.upper()

        name: str = input_params.get("i_name")
        if name is not None:
            name = name.replace(" ", "_")

        # REGISTER VARIABLES
        classname: str = input_params.get("i_classname")
        dalevel: str = input_params.get("i_data_access_level")

        # ELEMENT VARIABLES
        description: str = input_params.get("i_description")
        help_comment: str = input_params.get("i_help")

        # REGISTER TAB VARIABLES
        tab_level: str = input_params.get("i_tabLevel")
        window_id: str = input_params.get("i_window_id")
        sequence_number: str = input_params.get("i_sequence_number")

        # ADD_COLUMN VARIABLES
        column: str = input_params.get("i_column")
        column_type: str = input_params.get("i_column_type")
        default_value: str = input_params.get("i_default_value")
        can_be_null: bool = input_params.get("i_can_be_null")

        # ADD_FOREIGN VARIABLES
        parent_table: str = input_params.get("i_parent_table")
        child_table: str = input_params.get("i_child_table")
        external: bool = input_params.get("i_external")

        # WEBHOOK DATA
        record_id = input_params.get("i_record_id")
        clean_terminology = input_params.get("i_clean_terminology")
        key_word = input_params.get("i_key_word")

        # EXTRA INFO
        access_token = validate_extra_info()
        if "error" in access_token:
            return access_token
        etendo_host = utils.read_optional_env_var(
            "ETENDO_HOST", "http://host.docker.internal:8080/etendo"
        )
        copilot_debug(f"ETENDO_HOST: {etendo_host}")

        # MODE SELECTOR
        if mode == "REGISTER_TABLE":
            return register_table(
                etendo_host,
                access_token,
                prefix,
                name,
                classname,
                dalevel,
                description,
                help_comment,
            )
        elif mode == "REGISTER_COLUMNS":
            return register_columns(etendo_host, access_token, prefix, name)
        elif mode == "SYNC_TERMINOLOGY":
            return sync_terminoloy(etendo_host, access_token, clean_terminology)
        elif mode == "REGISTER_WINDOW":
            return register_window(
                etendo_host, access_token, prefix, name, description, help_comment
            )
        elif mode == "REGISTER_FIELDS":
            return register_fields(
                etendo_host, access_token, record_id, description, help_comment
            )
        elif mode == "CREATE_TABLE":
            return create_table(etendo_host, access_token, mode, prefix, name)
        elif mode == "ADD_COLUMN":
            return add_column(
                etendo_host,
                access_token,
                mode,
                prefix,
                name,
                column,
                column_type,
                default_value,
                can_be_null,
            )
        elif mode == "READ_ELEMENTS":
            return read_elements(etendo_host, access_token, mode, record_id)
        elif mode == "WRITE_ELEMENTS":
            return write_elements(
                etendo_host, access_token, mode, record_id, description, help_comment
            )
        elif mode == "ADD_FOREIGN":
            return add_foreign(
                etendo_host,
                access_token,
                mode,
                prefix,
                parent_table,
                child_table,
                external,
            )
        elif mode == "REGISTER_TAB":
            return register_tab(
                etendo_host,
                access_token,
                window_id,
                tab_level,
                description,
                help_comment,
                record_id,
                sequence_number,
            )
        elif mode == "GET_CONTEXT":
            return get_context(etendo_host, access_token, mode, name, key_word)
        else:
            return {"error": "Wrong Mode. Available modes are " + str(available_modes)}
