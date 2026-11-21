# Runs without AWS: the provider is mocked, nothing is created.
mock_provider "aws" {
  mock_data "aws_caller_identity" {
    defaults = {
      account_id = "123456789012"
    }
  }
}

variables {
  alert_email = "alerts@example.com"
}

run "deny_policy_blocks_costly_actions" {
  command = plan

  assert {
    condition     = aws_iam_policy.budget_deny.name == "value-screener-budget-deny"
    error_message = "Deny policy name must be value-screener-budget-deny."
  }

  assert {
    condition = alltrue([
      length(jsondecode(aws_iam_policy.budget_deny.policy).Statement) == 1,
      jsondecode(aws_iam_policy.budget_deny.policy).Statement[0].Effect == "Deny",
      jsondecode(aws_iam_policy.budget_deny.policy).Statement[0].Resource == "*",
    ])
    error_message = "Deny policy must be one Deny statement on all resources."
  }

  assert {
    condition = length(setsubtract([
      # from the old docs/aws/budget-deny-policy.json
      "ec2:RunInstances", "ec2:AllocateAddress", "ec2:CreateNatGateway", "ec2:CreateVpcEndpoint",
      "elasticloadbalancing:CreateLoadBalancer", "rds:CreateDBInstance", "rds:CreateDBCluster",
      "elasticache:CreateCacheCluster", "redshift:CreateCluster", "lambda:CreateFunction",
      "apprunner:CreateService", "ecs:CreateCluster", "ecs:RunTask", "sagemaker:CreateNotebookInstance",
      "wafv2:CreateWebACL", "guardduty:CreateDetector", "config:PutConfigurationRecorder",
      "route53:CreateHostedZone",
      # added for the serverless setup (design section 10)
      "lambda:InvokeFunction", "lambda:UpdateFunctionCode", "lambda:PublishVersion", "lambda:UpdateAlias",
      "dynamodb:CreateTable", "dynamodb:UpdateTable", "cloudfront:CreateInvalidation", "s3:PutObject",
    ], jsondecode(aws_iam_policy.budget_deny.policy).Statement[0].Action)) == 0
    error_message = "Deny policy is missing at least one required action."
  }
}

run "budget_is_5_usd_monthly_with_two_alerts" {
  command = plan

  assert {
    condition = alltrue([
      aws_budgets_budget.monthly.name == "value-screener-monthly",
      aws_budgets_budget.monthly.budget_type == "COST",
      aws_budgets_budget.monthly.limit_amount == "5",
      aws_budgets_budget.monthly.limit_unit == "USD",
      aws_budgets_budget.monthly.time_unit == "MONTHLY",
    ])
    error_message = "Budget must be value-screener-monthly, COST, 5 USD, MONTHLY."
  }

  assert {
    condition = toset([
      for n in aws_budgets_budget.monthly.notification :
      "${n.notification_type}:${n.threshold}:${n.threshold_type}:${join(",", n.subscriber_email_addresses)}"
      ]) == toset([
      "ACTUAL:1:ABSOLUTE_VALUE:alerts@example.com",
      "FORECASTED:3:ABSOLUTE_VALUE:alerts@example.com",
    ])
    error_message = "Alerts must be 1 USD actual and 3 USD forecast, sent to alert_email."
  }
}

run "budget_action_denies_backend_and_deploy_role_automatically" {
  command = plan

  assert {
    condition = alltrue([
      aws_budgets_budget_action.deny.budget_name == "value-screener-monthly",
      aws_budgets_budget_action.deny.action_type == "APPLY_IAM_POLICY",
      aws_budgets_budget_action.deny.approval_model == "AUTOMATIC",
      aws_budgets_budget_action.deny.notification_type == "ACTUAL",
      aws_budgets_budget_action.deny.action_threshold[0].action_threshold_type == "ABSOLUTE_VALUE",
      aws_budgets_budget_action.deny.action_threshold[0].action_threshold_value == 5,
    ])
    error_message = "Action must apply the IAM policy automatically at 5 USD actual."
  }

  assert {
    condition = (
      aws_budgets_budget_action.deny.definition[0].iam_action_definition[0].roles
      == toset(["value-screener-backend", "value-screener-github-deploy"])
    )
    error_message = "Action must target exactly the backend and the deploy role (never Admin)."
  }

  assert {
    condition     = one(aws_budgets_budget_action.deny.subscriber).address == "alerts@example.com"
    error_message = "Action must notify alert_email."
  }
}

run "execution_role_may_only_attach_the_deny_policy_to_the_two_roles" {
  command = plan

  assert {
    condition = jsondecode(aws_iam_role.budget_action.assume_role_policy).Statement == [{
      Effect    = "Allow"
      Principal = { Service = "budgets.amazonaws.com" }
      Action    = "sts:AssumeRole"
      Condition = { StringEquals = { "aws:SourceAccount" = "123456789012" } }
    }]
    error_message = "Execution role may only be assumed by AWS Budgets of this account."
  }

  assert {
    condition = jsondecode(aws_iam_role_policy.budget_action.policy).Statement == [{
      Effect = "Allow"
      Action = ["iam:AttachRolePolicy", "iam:DetachRolePolicy"]
      Resource = [
        "arn:aws:iam::123456789012:role/value-screener-backend",
        "arn:aws:iam::123456789012:role/value-screener-github-deploy",
      ]
      Condition = {
        ArnEquals = { "iam:PolicyARN" = "arn:aws:iam::123456789012:policy/value-screener-budget-deny" }
      }
    }]
    error_message = "Execution role may only attach/detach the deny policy on the two roles."
  }
}

run "alert_email_must_look_like_an_address" {
  command = plan

  variables {
    alert_email = "not-an-address"
  }

  expect_failures = [var.alert_email]
}
