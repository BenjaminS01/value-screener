locals {
  budget_deny_policy_name = "value-screener-budget-deny"
  # Roles the deny policy is attached to when the budget is exceeded. Never the Admin role.
  budget_deny_role_names = [aws_iam_role.backend.name, aws_iam_role.github_deploy.name]
}

# Attached automatically at 5 USD: stops calls to Lambda, deploys and creation of costly resources.
resource "aws_iam_policy" "budget_deny" {
  name        = local.budget_deny_policy_name
  description = "Attached by the budget action when value-screener-monthly exceeds 5 USD."

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Deny"
      Action = [
        # costly resources (from the former docs/aws/budget-deny-policy.json)
        "ec2:RunInstances",
        "ec2:AllocateAddress",
        "ec2:CreateNatGateway",
        "ec2:CreateVpcEndpoint",
        "elasticloadbalancing:CreateLoadBalancer",
        "rds:CreateDBInstance",
        "rds:CreateDBCluster",
        "elasticache:CreateCacheCluster",
        "redshift:CreateCluster",
        "lambda:CreateFunction",
        "apprunner:CreateService",
        "ecs:CreateCluster",
        "ecs:RunTask",
        "sagemaker:CreateNotebookInstance",
        "wafv2:CreateWebACL",
        "guardduty:CreateDetector",
        "config:PutConfigurationRecorder",
        "route53:CreateHostedZone",
        # serverless setup: no Lambda calls, no deploys, no table/capacity changes
        "lambda:InvokeFunction",
        "lambda:UpdateFunctionCode",
        "lambda:PublishVersion",
        "lambda:UpdateAlias",
        "dynamodb:CreateTable",
        "dynamodb:UpdateTable",
        "cloudfront:CreateInvalidation",
        "s3:PutObject",
      ]
      Resource = "*"
    }]
  })
}

# Role that AWS Budgets assumes to attach the deny policy.
resource "aws_iam_role" "budget_action" {
  name = "value-screener-budget-action"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "budgets.amazonaws.com" }
      Action    = "sts:AssumeRole"
      Condition = { StringEquals = { "aws:SourceAccount" = local.account_id } }
    }]
  })
}

# It may only attach/detach exactly the deny policy, and only on the two roles.
resource "aws_iam_role_policy" "budget_action" {
  name = "attach-budget-deny"
  role = aws_iam_role.budget_action.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["iam:AttachRolePolicy", "iam:DetachRolePolicy"]
      Resource = [for name in local.budget_deny_role_names : "arn:aws:iam::${local.account_id}:role/${name}"]
      Condition = {
        ArnEquals = {
          "iam:PolicyARN" = "arn:aws:iam::${local.account_id}:policy/${local.budget_deny_policy_name}"
        }
      }
    }]
  })
}

resource "aws_budgets_budget" "monthly" {
  name         = "value-screener-monthly"
  budget_type  = "COST"
  limit_amount = "5"
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 1
    threshold_type             = "ABSOLUTE_VALUE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = [var.alert_email]
  }

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 3
    threshold_type             = "ABSOLUTE_VALUE"
    notification_type          = "FORECASTED"
    subscriber_email_addresses = [var.alert_email]
  }
}

resource "aws_budgets_budget_action" "deny" {
  budget_name        = aws_budgets_budget.monthly.name
  action_type        = "APPLY_IAM_POLICY"
  approval_model     = "AUTOMATIC"
  notification_type  = "ACTUAL"
  execution_role_arn = aws_iam_role.budget_action.arn

  action_threshold {
    action_threshold_type  = "ABSOLUTE_VALUE"
    action_threshold_value = 5
  }

  definition {
    iam_action_definition {
      policy_arn = aws_iam_policy.budget_deny.arn
      roles      = local.budget_deny_role_names
    }
  }

  subscriber {
    address           = var.alert_email
    subscription_type = "EMAIL"
  }

  # The execution role needs its permissions before Budgets validates the action.
  depends_on = [aws_iam_role_policy.budget_action]
}
