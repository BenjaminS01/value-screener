locals {
  github_oidc_host = "token.actions.githubusercontent.com"
  # Built from the account id so the trust policy is known at plan time.
  github_oidc_provider_arn = "arn:aws:iam::${local.account_id}:oidc-provider/${local.github_oidc_host}"
}

# Lets AWS accept identity tokens issued by GitHub Actions (no access keys in GitHub).
resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://${local.github_oidc_host}"
  client_id_list = ["sts.amazonaws.com"]
}

# Assumed by the deploy workflow, only for version tags (v*) of this repository.
resource "aws_iam_role" "github_deploy" {
  name = "value-screener-github-deploy"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = local.github_oidc_provider_arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = { "${local.github_oidc_host}:aud" = "sts.amazonaws.com" }
        StringLike   = { "${local.github_oidc_host}:sub" = "repo:${var.github_repository}:ref:refs/tags/v*" }
      }
    }]
  })

  depends_on = [aws_iam_openid_connect_provider.github]
}

# Part 2 permissions: artifact bucket only. Part 3 (app) attaches Lambda, frontend and CloudFront rights.
resource "aws_iam_role_policy" "github_deploy_artifacts" {
  name = "artifact-bucket"
  role = aws_iam_role.github_deploy.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "ReadWriteArtifacts"
        Effect   = "Allow"
        Action   = ["s3:GetObject", "s3:PutObject"]
        Resource = "${local.artifact_bucket_arn}/*"
      },
      {
        Sid      = "ListArtifacts"
        Effect   = "Allow"
        Action   = "s3:ListBucket"
        Resource = local.artifact_bucket_arn
      },
    ]
  })
}
