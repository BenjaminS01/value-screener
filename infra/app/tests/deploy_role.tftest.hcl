# Runs without AWS: the providers are mocked, nothing is created.
mock_provider "aws" {
  # Computed values (ARNs) get the mock values already during plan, so the policy can be asserted.
  override_during = plan

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

# Each lookup / resource gets its own ARN, so the test can tell them apart.
override_data {
  target = data.aws_iam_role.github_deploy
  values = {
    name = "value-screener-github-deploy"
    arn  = "arn:aws:iam::123456789012:role/value-screener-github-deploy"
  }
}

override_resource {
  target          = aws_lambda_function.backend
  override_during = plan
  values = {
    arn = "arn:aws:lambda:eu-central-1:123456789012:function:value-screener-backend"
  }
}

override_resource {
  target          = aws_lambda_function.deep_analysis
  override_during = plan
  values = {
    arn = "arn:aws:lambda:eu-central-1:123456789012:function:deep-analysis-demo"
  }
}

override_resource {
  target          = aws_s3_bucket.frontend
  override_during = plan
  values = {
    arn = "arn:aws:s3:::value-screener-frontend-123456789012"
  }
}

override_resource {
  target          = aws_cloudfront_distribution.main
  override_during = plan
  values = {
    arn = "arn:aws:cloudfront::123456789012:distribution/EDFDVBD6EXAMPLE"
  }
}

variables {
  admin_username = "admin-test"
}

run "deploy_role_may_only_ship_code_frontend_and_invalidate" {
  command = plan

  assert {
    condition     = aws_iam_role_policy.github_deploy.role == "value-screener-github-deploy"
    error_message = "The policy belongs on the deploy role from foundation (found by its fixed name)."
  }

  assert {
    condition     = length(jsondecode(aws_iam_role_policy.github_deploy.policy).Statement) == 3
    error_message = "Exactly three statements: Lambda, frontend bucket, CloudFront."
  }

  assert {
    condition = (
      [for s in jsondecode(aws_iam_role_policy.github_deploy.policy).Statement : s if s.Sid == "DeployLambdaCode"][0].Action
      == [
        "lambda:GetFunction",
        "lambda:GetFunctionConfiguration",
        "lambda:PublishVersion",
        "lambda:UpdateAlias",
        "lambda:UpdateFunctionCode",
      ]
    )
    error_message = "Lambda: only read, update code, publish a version and move the alias (decision 8)."
  }

  assert {
    condition = (
      [for s in jsondecode(aws_iam_role_policy.github_deploy.policy).Statement : s if s.Sid == "DeployLambdaCode"][0].Resource
      == [
        "arn:aws:lambda:eu-central-1:123456789012:function:value-screener-backend",
        "arn:aws:lambda:eu-central-1:123456789012:function:value-screener-backend:*",
        "arn:aws:lambda:eu-central-1:123456789012:function:deep-analysis-demo",
        "arn:aws:lambda:eu-central-1:123456789012:function:deep-analysis-demo:*",
      ]
    )
    error_message = "Lambda rights only on the two functions (plain ARN plus versions/alias for the waiters)."
  }

  assert {
    condition = (
      [for s in jsondecode(aws_iam_role_policy.github_deploy.policy).Statement : s if s.Sid == "SyncFrontend"][0].Action
      == ["s3:DeleteObject", "s3:GetObject", "s3:ListBucket", "s3:PutObject"]
      && [for s in jsondecode(aws_iam_role_policy.github_deploy.policy).Statement : s if s.Sid == "SyncFrontend"][0].Resource
      == [
        "arn:aws:s3:::value-screener-frontend-123456789012",
        "arn:aws:s3:::value-screener-frontend-123456789012/*",
      ]
    )
    error_message = "Frontend: s3 sync --delete on the frontend bucket only."
  }

  assert {
    condition = (
      [for s in jsondecode(aws_iam_role_policy.github_deploy.policy).Statement : s if s.Sid == "InvalidateCdn"][0].Action
      == "cloudfront:CreateInvalidation"
      && [for s in jsondecode(aws_iam_role_policy.github_deploy.policy).Statement : s if s.Sid == "InvalidateCdn"][0].Resource
      == "arn:aws:cloudfront::123456789012:distribution/EDFDVBD6EXAMPLE"
    )
    error_message = "CloudFront: only invalidations, only on this distribution."
  }
}
