# Runs without AWS: the providers are mocked, nothing is created.
mock_provider "aws" {
  # Computed values (ARNs) get the mock defaults already during plan, so they can be asserted.
  override_during = plan

  mock_resource "aws_apigatewayv2_api" {
    defaults = {
      execution_arn = "arn:aws:execute-api:eu-central-1:123456789012:abcde12345"
      api_endpoint  = "https://abcde12345.execute-api.eu-central-1.amazonaws.com"
    }
  }

  mock_resource "aws_lambda_alias" {
    defaults = {
      arn = "arn:aws:lambda:eu-central-1:123456789012:function:value-screener-backend:live"
    }
  }

  mock_data "aws_caller_identity" {
    defaults = {
      account_id = "123456789012"
    }
  }

  mock_data "aws_iam_role" {
    defaults = {
      name = "value-screener-backend"
      arn  = "arn:aws:iam::123456789012:role/value-screener-backend"
    }
  }

  mock_data "aws_dynamodb_table" {
    defaults = {
      name = "value-screener"
      arn  = "arn:aws:dynamodb:eu-central-1:123456789012:table/value-screener"
    }
  }
}

mock_provider "random" {}

variables {
  admin_username = "admin-test"
}

run "http_api_routes_api_paths_to_the_backend_alias" {
  command = plan

  assert {
    condition     = aws_apigatewayv2_api.main.protocol_type == "HTTP"
    error_message = "HTTP API (cheaper and simpler than REST API)."
  }

  assert {
    condition = (
      aws_apigatewayv2_integration.backend.integration_type == "AWS_PROXY"
      && aws_apigatewayv2_integration.backend.payload_format_version == "2.0"
      && aws_apigatewayv2_integration.backend.integration_uri == "arn:aws:lambda:eu-central-1:123456789012:function:value-screener-backend:live"
    )
    error_message = "Lambda proxy integration, payload 2.0 (StreamLambdaHandler), on the alias live."
  }

  assert {
    condition     = aws_apigatewayv2_route.api.route_key == "ANY /api/{proxy+}"
    error_message = "Only /api/* reaches the backend; everything else is answered by API Gateway with 404."
  }
}

run "stage_throttles_to_rate_5_burst_10" {
  command = plan

  assert {
    condition = (
      aws_apigatewayv2_stage.default.name == "$default"
      && aws_apigatewayv2_stage.default.auto_deploy
    )
    error_message = "Default stage (no stage name in the URL), deployed automatically."
  }

  assert {
    condition = (
      aws_apigatewayv2_stage.default.default_route_settings[0].throttling_rate_limit == 5
      && aws_apigatewayv2_stage.default.default_route_settings[0].throttling_burst_limit == 10
    )
    error_message = "User decision 2026-11-23: rate 5/s, burst 10 (flood cost ~17 $/month instead of ~35 $)."
  }
}

run "only_this_api_may_invoke_the_backend_alias" {
  command = plan

  assert {
    condition = (
      aws_lambda_permission.api.principal == "apigateway.amazonaws.com"
      && aws_lambda_permission.api.action == "lambda:InvokeFunction"
      && aws_lambda_permission.api.function_name == "value-screener-backend"
      && aws_lambda_permission.api.qualifier == "live"
      && aws_lambda_permission.api.source_arn == "arn:aws:execute-api:eu-central-1:123456789012:abcde12345/*/*"
    )
    error_message = "API Gateway may invoke only the alias live, and only from this API."
  }
}
