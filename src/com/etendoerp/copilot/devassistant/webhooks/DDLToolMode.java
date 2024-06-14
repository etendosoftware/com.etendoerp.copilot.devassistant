package com.etendoerp.copilot.devassistant.webhooks;

/**
 * Class containing constants for various DDLTool modes used in webhooks.
 */
public class DDLToolMode {

  private DDLToolMode(){

  }

  /**
   * Constant representing the "CREATE_TABLE" mode, used to create a new table.
   */
  public static final String CREATE_TABLE = "CREATE_TABLE";

  /**
   * Constant representing the "READ_ELEMENTS" mode, used to read elements from a table.
   */
  public static final String READ_ELEMENTS = "READ_ELEMENTS";

  /**
   * Constant representing the "WRITE_ELEMENTS" mode, used to write information to elements in a table.
   */
  public static final String WRITE_ELEMENTS = "WRITE_ELEMENTS";

  /**
   * Constant representing the "ADD_COLUMN" mode, used to add a new column to a table.
   */
  public static final String ADD_COLUMN = "ADD_COLUMN";

  /**
   * Constant representing the "ADD_FOREIGN" mode, used to add a foreign key constraint to a table.
   */
  public static final String ADD_FOREIGN = "ADD_FOREIGN";

  /**
   * Constant representing the "GET_CONTEXT" mode, used to retrieve the context information.
   */
  public static final String GET_CONTEXT = "GET_CONTEXT";
}
