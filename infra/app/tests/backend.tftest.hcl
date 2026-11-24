# Runs without AWS: the providers are mocked, nothing is created.
mock_provider "aws" {
  # Computed values (ARNs) get the mock defaults already during plan, so policies can be asserted.
  override_during = plan

  mock_resource "aws_cloudwatch_log_group" {
    defaults = {
      arn = "arn:aws:logs:eu-central-1:123456789012:log-group:/aws/lambda/value-screener-backend"
    }
  }

  mock_resource "aws_lambda_alias" {
    defaults = {
      arn = "arn:aws:lambda:eu-central-1:123456789012:function:deep-analysis-demo:live"
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

run "backend_lambda_runs_spring_with_snapstart" {
  command = plan

  assert {
    condition = (
      aws_lambda_function.backend.function_name == "value-screener-backend"
      && aws_lambda_function.backend.role == "arn:aws:iam::123456789012:role/value-screener-backend"
      && aws_lambda_function.backend.runtime == "java21"
      && aws_lambda_function.backend.handler == "com.valuescreener.lambda.StreamLambdaHandler::handleRequest"
    )
    error_message = "Name, foundation role, java21 and the Spring handler are fixed."
  }

  assert {
    condition     = aws_lambda_function.backend.memory_size == 1024 && aws_lambda_function.backend.timeout == 29
    error_message = "Design 7.1: 1024 MB, timeout 29 s (API Gateway limit 30 s)."
  }

  assert {
    condition     = aws_lambda_function.backend.snap_start[0].apply_on == "PublishedVersions"
    error_message = "SnapStart on published versions."
  }

  assert {
    condition = (
      aws_lambda_function.backend.publish
      && aws_lambda_function.backend.reserved_concurrent_executions == null
      && aws_lambda_function.backend.s3_bucket == "value-screener-artifacts-123456789012"
      && aws_lambda_function.backend.s3_key == "backend/backend.zip"
    )
    error_message = "Published version from the fixed key, no reserved concurrency."
  }

  assert {
    condition = aws_lambda_function.backend.environment[0].variables == tomap({
      SPRING_PROFILES_ACTIVE    = "aws"
      ADMIN_USERNAME            = "admin-test"
      APP_DYNAMODB_TABLE_NAME   = "value-screener"
      APP_RESEARCH_SYNC_ENABLED = "true"
    })
    error_message = "Exactly these four environment variables; secrets (incl. the sync token hash) come from SSM, never from env."
  }

  assert {
    condition = (
      aws_cloudwatch_log_group.backend.name == "/aws/lambda/value-screener-backend"
      && aws_cloudwatch_log_group.backend.retention_in_days == 14
    )
    error_message = "Log group with 14 days retention."
  }

  assert {
    condition     = aws_lambda_alias.backend_live.name == "live"
    error_message = "API Gateway invokes the alias live."
  }
}

run "backend_role_gets_only_what_the_app_uses" {
  command = plan

  assert {
    condition     = aws_iam_role_policy.backend.role == "value-screener-backend"
    error_message = "Permissions are attached to the foundation role by name."
  }

  assert {
    condition = {
      for s in jsondecode(aws_iam_role_policy.backend.policy).Statement : s.Sid => [s.Action, s.Resource]
      } == {
      DynamoTable = [
        ["dynamodb:DeleteItem", "dynamodb:GetItem", "dynamodb:PutItem", "dynamodb:Query", "dynamodb:UpdateItem"],
        "arn:aws:dynamodb:eu-central-1:123456789012:table/value-screener",
      ]
      ReadOwnParameters = [
        "ssm:GetParametersByPath",
        [
          "arn:aws:ssm:eu-central-1:123456789012:parameter/value-screener",
          "arn:aws:ssm:eu-central-1:123456789012:parameter/value-screener/*",
        ],
      ]
      InvokeDeepAnalysisLive = [
        "lambda:InvokeFunction",
        "arn:aws:lambda:eu-central-1:123456789012:function:deep-analysis-demo:live",
      ]
      WriteOwnLogs = [
        ["logs:CreateLogStream", "logs:PutLogEvents"],
        "arn:aws:logs:eu-central-1:123456789012:log-group:/aws/lambda/value-screener-backend:*",
      ]
    }
    error_message = "Backend permissions must be exactly: table items, own SSM prefix, deep-analysis alias, own logs."
  }
}

run "origin_secret_is_random_and_stored_for_the_backend" {
  command = plan

  assert {
    condition = (
      random_password.origin_verify.length == 48
      && random_password.origin_verify.special == false
    )
    error_message = "48 random letters/digits (safe in an HTTP header)."
  }

  assert {
    condition = (
      aws_ssm_parameter.origin_verify.name == "/value-screener/app.security.origin-verify.secret"
      && aws_ssm_parameter.origin_verify.type == "SecureString"
    )
    error_message = "Exact parameter name from application-aws.yml, encrypted."
  }
}

run "admin_username_must_be_usable" {
  command = plan

  variables {
    admin_username = "bad name*"
  }

  expect_failures = [var.admin_username]
}
