# Runs without AWS: the provider is mocked, nothing is created.
mock_provider "aws" {
  mock_data "aws_caller_identity" {
    defaults = {
      account_id = "123456789012"
    }
  }
}

run "github_oidc_provider_trusts_only_sts_audience" {
  command = plan

  assert {
    condition     = aws_iam_openid_connect_provider.github.url == "https://token.actions.githubusercontent.com"
    error_message = "OIDC provider must point to GitHub Actions."
  }

  assert {
    condition     = aws_iam_openid_connect_provider.github.client_id_list == toset(["sts.amazonaws.com"])
    error_message = "OIDC audience must be sts.amazonaws.com only."
  }
}

run "deploy_role_only_for_version_tags_of_this_repo" {
  command = plan

  assert {
    condition     = aws_iam_role.github_deploy.name == "value-screener-github-deploy"
    error_message = "Deploy role name must be value-screener-github-deploy."
  }

  assert {
    condition = jsondecode(aws_iam_role.github_deploy.assume_role_policy).Statement == [{
      Effect = "Allow"
      Principal = {
        Federated = "arn:aws:iam::123456789012:oidc-provider/token.actions.githubusercontent.com"
      }
      Action = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = { "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com" }
        StringLike   = { "token.actions.githubusercontent.com:sub" = "repo:BenjaminS01/value-screener:ref:refs/tags/v*" }
      }
    }]
    error_message = "Deploy role may only be assumed by version tags (v*) of BenjaminS01/value-screener."
  }

  assert {
    condition = jsondecode(aws_iam_role_policy.github_deploy_artifacts.policy).Statement == [
      {
        Sid      = "ReadWriteArtifacts"
        Effect   = "Allow"
        Action   = ["s3:GetObject", "s3:PutObject"]
        Resource = "arn:aws:s3:::value-screener-artifacts-123456789012/*"
      },
      {
        Sid      = "ListArtifacts"
        Effect   = "Allow"
        Action   = "s3:ListBucket"
        Resource = "arn:aws:s3:::value-screener-artifacts-123456789012"
      },
    ]
    error_message = "Deploy role may only read, write and list the artifact bucket."
  }
}

run "backend_role_is_an_empty_lambda_role" {
  command = plan

  assert {
    condition     = aws_iam_role.backend.name == "value-screener-backend"
    error_message = "Backend role name must be value-screener-backend."
  }

  assert {
    condition = jsondecode(aws_iam_role.backend.assume_role_policy).Statement == [{
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
    error_message = "Backend role may only be assumed by Lambda."
  }

  assert {
    condition     = output.backend_role_name == "value-screener-backend"
    error_message = "Output backend_role_name must return the role name."
  }
}

run "github_repository_must_be_owner_slash_name" {
  command = plan

  variables {
    github_repository = "value-screener"
  }

  expect_failures = [var.github_repository]
}
