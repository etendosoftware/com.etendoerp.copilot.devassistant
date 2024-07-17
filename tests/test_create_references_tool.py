from unittest.mock import MagicMock

import pytest
from pytest_lazyfixture import lazy_fixture
from langsmith import unit

from copilot.core.threadcontext import ThreadContext
from tools import CreateReferencesTool


# Fixture for the successful mock response
@pytest.fixture
def mock_response_success():
    mock_response = MagicMock()
    mock_response.ok = True
    mock_response.json.return_value = {"result": "success"}
    return mock_response


# Fixture for the mock of requests.post
@pytest.fixture
def mock_requests_post(monkeypatch):
    mock_post = MagicMock()
    monkeypatch.setattr("requests.post", mock_post)
    return mock_post


# Fixture for the valid input parameters
@pytest.fixture
def valid_input_params():
    return {
        "i_prefix": "MOD",
        "i_name": "Test Reference",
        "i_reference_list": "item1,item2,item3",
        "i_help": "Help text example",
        "i_description": "Description example"
    }


# Fixture to simulate thread context with access token
@pytest.fixture
def thread_context_extra_info(monkeypatch):
    extra_info = {'auth': {'ETENDO_TOKEN': 'test_token'}}
    monkeypatch.setattr(ThreadContext, 'get_data', lambda key: extra_info)
    return extra_info


# Test for valid inputs
@unit
def test_create_references_valid(valid_input_params, mock_requests_post, thread_context_extra_info):
    tool = CreateReferencesTool()

    # Setup the mock for the successful response
    mock_response = MagicMock()
    mock_response.ok = True
    mock_response.json.return_value = {"result": "success"}
    mock_requests_post.return_value = mock_response

    ThreadContext.set_data('extra_info', thread_context_extra_info)

    result = tool.run(valid_input_params)

    assert "error" not in result, "Should not return an error for valid inputs."
    assert result == {"result": "success"}


# Test for handling missing token
@unit
def test_create_references_tool_no_token(valid_input_params, monkeypatch):
    monkeypatch.setattr(ThreadContext, 'get_data', lambda key: {})

    tool = CreateReferencesTool()
    response = tool.run(valid_input_params)

    assert "error" in response
    expected_error_message = "No access token provided. To work with Etendo, an access token is required.Make sure that the Webservices are enabled for the user role and the WS are configured for the Entity."
    assert response["error"] == expected_error_message


# Parameterized test for different expected responses
@unit
@pytest.mark.parametrize(
    "expected_response, mock_response",
    [
        ({"result": "success"}, pytest.lazy_fixture('mock_response_success')),
        ({
             "error": "No access token provided. To work with Etendo, an access token is required.Make sure that the "
                      "Webservices are enabled for the user role and the WS are configured for the Entity."},
         None)
    ],
)
def test_create_references_tool(mock_requests_post, valid_input_params, thread_context_extra_info, expected_response,
                                mock_response, monkeypatch):
    if mock_response:
        mock_requests_post.return_value = mock_response
        ThreadContext.set_data('extra_info', thread_context_extra_info)
    else:
        monkeypatch.setattr(ThreadContext, 'get_data', lambda key: {})

    tool = CreateReferencesTool()
    response = tool.run(valid_input_params)

    # Convert the response to dict if it's a MagicMock to simulate the actual behavior
    if isinstance(response, MagicMock):
        response = response()

    # Debug message for test results
    print(f"Response: {response}")
    print(f"Expected Response: {expected_response}")

    assert response == expected_response
