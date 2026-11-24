# Rights for the GitHub deploy workflow (.github/workflows/deploy.yml), on the role from foundation.
# Foundation already allows the artifact bucket (zip upload; Lambda reads the zip with the caller's rights).

data "aws_iam_role" "github_deploy" {
  name = "value-screener-github-deploy"
}

resource "aws_iam_role_policy" "github_deploy" {
  name = "app-deploy"
  role = data.aws_iam_role.github_deploy.name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        # New code → publish a version → move alias live. ":*" covers versions and the alias, which the
        # waiters (function-updated, published-version-active) and update-alias address.
        Sid    = "DeployLambdaCode"
        Effect = "Allow"
        Action = [
          "lambda:GetFunction",
          "lambda:GetFunctionConfiguration",
          "lambda:PublishVersion",
          "lambda:UpdateAlias",
          "lambda:UpdateFunctionCode",
        ]
        Resource = [
          aws_lambda_function.backend.arn,
          "${aws_lambda_function.backend.arn}:*",
          aws_lambda_function.deep_analysis.arn,
          "${aws_lambda_function.deep_analysis.arn}:*",
        ]
      },
      {
        # aws s3 sync --delete of frontend/dist.
        Sid      = "SyncFrontend"
        Effect   = "Allow"
        Action   = ["s3:DeleteObject", "s3:GetObject", "s3:ListBucket", "s3:PutObject"]
        Resource = [aws_s3_bucket.frontend.arn, "${aws_s3_bucket.frontend.arn}/*"]
      },
      {
        Sid      = "InvalidateCdn"
        Effect   = "Allow"
        Action   = "cloudfront:CreateInvalidation"
        Resource = aws_cloudfront_distribution.main.arn
      },
    ]
  })
}
