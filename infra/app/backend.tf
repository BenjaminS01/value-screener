# Spring Boot backend in Lambda, behind API Gateway (Task 6) and CloudFront (Task 7).

resource "aws_cloudwatch_log_group" "backend" {
  name              = "/aws/lambda/value-screener-backend"
  retention_in_days = 14
}

# Everything the backend may do, on the empty role from foundation. Only what the code really calls:
# item reads/writes and queries (no Scan, no table changes), its own SSM prefix, the deep-analysis alias, its log.
resource "aws_iam_role_policy" "backend" {
  name = "app"
  role = data.aws_iam_role.backend.name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "DynamoTable"
        Effect   = "Allow"
        Action   = ["dynamodb:DeleteItem", "dynamodb:GetItem", "dynamodb:PutItem", "dynamodb:Query", "dynamodb:UpdateItem"]
        Resource = data.aws_dynamodb_table.main.arn
      },
      {
        # Spring Cloud AWS loads /value-screener/ with GetParametersByPath (decrypted with the AWS-managed key
        # aws/ssm, which needs no extra KMS permission).
        Sid    = "ReadOwnParameters"
        Effect = "Allow"
        Action = "ssm:GetParametersByPath"
        Resource = [
          "arn:aws:ssm:${local.region}:${local.account_id}:parameter/value-screener",
          "arn:aws:ssm:${local.region}:${local.account_id}:parameter/value-screener/*",
        ]
      },
      {
        Sid      = "InvokeDeepAnalysisLive"
        Effect   = "Allow"
        Action   = "lambda:InvokeFunction"
        Resource = aws_lambda_alias.deep_analysis_live.arn
      },
      {
        Sid      = "WriteOwnLogs"
        Effect   = "Allow"
        Action   = ["logs:CreateLogStream", "logs:PutLogEvents"]
        Resource = "${aws_cloudwatch_log_group.backend.arn}:*"
      },
    ]
  })
}

resource "aws_lambda_function" "backend" {
  function_name = "value-screener-backend"
  role          = data.aws_iam_role.backend.arn
  runtime       = "java21"
  handler       = "com.valuescreener.lambda.StreamLambdaHandler::handleRequest"
  memory_size   = 1024
  timeout       = 29

  # First zip is uploaded by hand; afterwards CI uploads to the same key and calls update-function-code.
  s3_bucket = local.artifact_bucket_name
  s3_key    = "backend/backend.zip"
  publish   = true

  # Each published version starts from a snapshot taken after Spring's init (reads SSM once, at init).
  snap_start {
    apply_on = "PublishedVersions"
  }

  environment {
    variables = {
      SPRING_PROFILES_ACTIVE  = "aws"
      ADMIN_USERNAME          = var.admin_username
      APP_DYNAMODB_TABLE_NAME = data.aws_dynamodb_table.main.name
      # POST /api/research/sync/snapshots (scripts/push-research.sh). Needs the real hash in SSM
      # /value-screener/app.research-sync.token-hash (infra/data), else the backend refuses to start.
      # Reaches live only with the next CI deploy (new version), like every config change here.
      APP_RESEARCH_SYNC_ENABLED = "true"
    }
  }

  lifecycle {
    # Code belongs to CI, not to Terraform.
    ignore_changes = [s3_key, s3_object_version, source_code_hash]
  }

  # Init (and the SnapStart snapshot) needs the permissions, the log group and the origin secret in SSM.
  depends_on = [aws_cloudwatch_log_group.backend, aws_iam_role_policy.backend, aws_ssm_parameter.origin_verify]
}

resource "aws_lambda_alias" "backend_live" {
  name             = "live"
  function_name    = aws_lambda_function.backend.function_name
  function_version = aws_lambda_function.backend.version

  lifecycle {
    # CI points live at each new version.
    ignore_changes = [function_version]
  }
}
