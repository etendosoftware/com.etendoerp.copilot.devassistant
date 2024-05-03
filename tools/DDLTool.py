from datetime import datetime
from typing import Dict, Type, Optional
from uuid import uuid4
import psycopg2
from pydantic import BaseModel, Field

from copilot.core.tool_wrapper import ToolWrapper


class DDLToolInput(BaseModel):
    i_prefix: str = Field(
        title="Prefix",
        description="This is the prefix of the module in database."
    )
    i_name: str = Field(
        title="Name",
        description="This is the name of the table, this construct the database name adding the prefix before and a '_'."
    )
    i_classname: Optional[str] = Field(
        None,
        title="ClassName",
        description="This is the java class name associated to the table, if this is not provided will be generated automatically."
    )


def get_data_package(prefix, i_database, i_username, i_password, i_hostname, i_port):
    conn = psycopg2.connect(database=i_database, user=i_username, password=i_password, host=i_hostname, port=i_port)
    cursor = conn.cursor()

    query = """
            SELECT pck.ad_package_id
            FROM ad_module_dbprefix mpre
            LEFT JOIN ad_module mo ON mo.ad_module_id = mpre.ad_module_id
            LEFT JOIN ad_package pck ON pck.ad_module_id = mo.ad_module_id
            WHERE mpre."name" ilike %s
       """
    cursor.execute(query,(prefix,))
    ad_package_id = cursor.fetchall()

    # Cerrar la conexión a la base de datos
    cursor.close()
    conn.close()

    return ad_package_id

def get_table_id(i_database,i_username,i_password,i_hostname,i_port):
    conn = psycopg2.connect(database=i_database, user=i_username, password=i_password, host=i_hostname,port=i_port)
    cursor = conn.cursor()
    query = """
            SELECT get_uuid()
            """
    cursor.execute(query)
    table_id = cursor.fetchall()
    cursor.close()
    conn.close()

    return table_id


class DDLTool(ToolWrapper):
    name = 'DDLTool'
    description = "This tool register a table on the AD_Table in Etendo."
    args_schema: Type[BaseModel] = DDLToolInput

    def run(self, input_params: Dict, *args, **kwargs):

        import psycopg2
        from datetime import datetime

        i_hostname: str = 'localhost'
        i_username: str = 'tad'
        i_password: str = 'tad'
        i_database: str = 'etendo2'
        i_port: str = '5433'

        # Establishing connection
        db_uri = f"postgresql://{i_username}:{i_password}@{i_hostname}:{i_port}/{i_database}"

        prefix = input_params.get('i_prefix')
        tablename: str = prefix + '_' + input_params.get('i_name')
        classname: str = input_params.get('i_classname')
        if  classname is None:
            classname = tablename.replace("_","")

        datapackage = get_data_package(prefix, i_database, i_username, i_password, i_hostname, i_port)
        ad_table_id = get_table_id(i_database, i_username, i_password, i_hostname, i_port)

        conn = psycopg2.connect(db_uri)
        cursor = conn.cursor()

        data = {
                "AD_Table_ID": ad_table_id[0],
                "AD_Client_ID": '0',
                "AD_Org_ID": '0',
                "IsActive": 'Y',
                "Created": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
                "CreatedBy": '0',
                "Updated": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
                "UpdatedBy": '0',
                "AccessLevel": '3',
                "AD_Package_ID": datapackage[0],
                "Name": tablename,
                "TableName": tablename,
                "ClassName": classname,
                "Description": input_params.get('i_name')
                }

        # Formulate the SQL INSERT query
        columns = ', '.join(data.keys())
        placeholders = ', '.join(['%s'] * len(data))
        values = tuple(data.values())

        query = f"INSERT INTO PUBLIC.AD_TABLE ({columns}) VALUES ({placeholders});"

        try:
            cursor.execute(query, values)
            conn.commit()
            message = "The insertion was completed successfully."
        except Exception as e:
            conn.rollback()
            message = f"An error occurred: {e}"
        finally:
            cursor.close()
            conn.close()


        return {"message": message}