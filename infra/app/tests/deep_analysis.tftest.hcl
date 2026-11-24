# Runs without AWS: the provider is mocked, nothing is created.
mock_provider "aws" {
  # Computed values (ARNs) get the mock defaults already during plan, so policies can be asserted.
  override_during = plan

  mock_resource "aws_cloudwatch_log_group" {
    defaults = {
      arn = "arn:aws:logs:eu-central-1:123456789012:log-group:/aws/lambda/deep-analysis-demo"
    }
  }

  mock_data "aws_caller_identity" {
    defaults = {
      account_id = "123456789012"
    }
  }

  # Lookups for the backend (same stack): valid ARNs so the plan of the whole stack works.
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

run "deep_analysis_lambda_is_small_short_and_logs_14_days" {
  command = plan

  assert {
    condition = (
      aws_lambda_function.deep_analysis.function_name == "deep-analysis-demo"
      && aws_lambda_function.deep_analysis.runtime == "java21"
      && aws_lambda_function.deep_analysis.handler == "io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest"
    )
    error_message = "Name (backend calls deep-analysis-demo), runtime java21 and the Quarkus handler are fixed."
  }

  assert {
    condition = (
      aws_lambda_function.deep_analysis.memory_size == 512
      && aws_lambda_function.deep_analysis.timeout == 10
    )
    error_message = "Design: memory 256-512 MB, timeout <= 10 s."
  }

  assert {
    condition     = aws_lambda_function.deep_analysis.reserved_concurrent_executions == null
    error_message = "No reserved concurrency (account quota is 10, user decision)."
  }

  assert {
    condition = (
      aws_lambda_function.deep_analysis.publish
      && aws_lambda_function.deep_analysis.s3_bucket == "value-screener-artifacts-123456789012"
      && aws_lambda_function.deep_analysis.s3_key == "deep-analysis/deep-analysis.zip"
    )
    error_message = "Code comes from the fixed key in the artifact bucket and is published as a version."
  }

  assert {
    condition = (
      aws_cloudwatch_log_group.deep_analysis.name == "/aws/lambda/deep-analysis-demo"
      && aws_cloudwatch_log_group.deep_analysis.retention_in_days == 14
    )
    error_message = "Log group must be the Lambda's default name with 14 days retention."
  }

  assert {
    condition     = aws_lambda_alias.deep_analysis_live.name == "live"
    error_message = "The backend invokes the alias live."
  }

  assert {
    condition     = aws_iam_role.deep_analysis.name == "value-screener-deep-analysis"
    error_message = "Own role, separate from the backend role."
  }

  assert {
    condition = (
      jsondecode(aws_iam_role_policy.deep_analysis_logs.policy).Statement[0].Action
      == ["logs:CreateLogStream", "logs:PutLogEvents"]
      && jsondecode(aws_iam_role_policy.deep_analysis_logs.policy).Statement[0].Resource
      == "arn:aws:logs:eu-central-1:123456789012:log-group:/aws/lambda/deep-analysis-demo:*"
    )
    error_message = "The role may only write to its own log group (no CreateLogGroup, nothing else)."
  }
}
