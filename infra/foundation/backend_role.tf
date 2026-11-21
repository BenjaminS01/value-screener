# Identity of the backend Lambda. Created empty here so the budget action can target it from the start;
# part 3 (app) attaches its permissions (DynamoDB, SSM, logs, invoke deep-analysis) and uses it for the Lambda.
resource "aws_iam_role" "backend" {
  name = "value-screener-backend"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}
