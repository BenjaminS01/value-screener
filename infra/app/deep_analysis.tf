# Quarkus demo Lambda, invoked only by the backend (alias live). No SnapStart: Quarkus starts fast enough.

resource "aws_iam_role" "deep_analysis" {
  name = "value-screener-deep-analysis"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

# Created by Terraform (not by Lambda on first log) so the retention is set; the role cannot create log groups.
resource "aws_cloudwatch_log_group" "deep_analysis" {
  name              = "/aws/lambda/deep-analysis-demo"
  retention_in_days = 14
}

resource "aws_iam_role_policy" "deep_analysis_logs" {
  name = "logs"
  role = aws_iam_role.deep_analysis.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["logs:CreateLogStream", "logs:PutLogEvents"]
      Resource = "${aws_cloudwatch_log_group.deep_analysis.arn}:*"
    }]
  })
}

resource "aws_lambda_function" "deep_analysis" {
  function_name = "deep-analysis-demo"
  role          = aws_iam_role.deep_analysis.arn
  runtime       = "java21"
  handler       = "io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest"
  memory_size   = 512
  timeout       = 10

  # First zip is uploaded by hand; afterwards CI uploads to the same key and calls update-function-code.
  s3_bucket = local.artifact_bucket_name
  s3_key    = "deep-analysis/deep-analysis.zip"
  publish   = true

  lifecycle {
    # Code belongs to CI, not to Terraform.
    ignore_changes = [s3_key, s3_object_version, source_code_hash]
  }

  depends_on = [aws_cloudwatch_log_group.deep_analysis, aws_iam_role_policy.deep_analysis_logs]
}

resource "aws_lambda_alias" "deep_analysis_live" {
  name             = "live"
  function_name    = aws_lambda_function.deep_analysis.function_name
  function_version = aws_lambda_function.deep_analysis.version

  lifecycle {
    # CI points live at each new version.
    ignore_changes = [function_version]
  }
}
