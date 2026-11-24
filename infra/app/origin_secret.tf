# Proves that a request came through CloudFront (design 7.3). CloudFront sends it as X-Origin-Verify (Task 7), the
# backend compares it with this SSM value and answers 403 otherwise. Accepted: the value is in the (private,
# encrypted) app state, because CloudFront's config needs it in plain text anyway. Rotation:
#   terraform apply -replace=random_password.origin_verify   (then deploy a new backend version: SnapStart)
resource "random_password" "origin_verify" {
  length  = 48
  special = false
}

resource "aws_ssm_parameter" "origin_verify" {
  name        = "/value-screener/app.security.origin-verify.secret"
  type        = "SecureString"
  tier        = "Standard"
  value       = random_password.origin_verify.result
  description = "Managed by Terraform (infra/app). Shared secret between CloudFront and the backend."
}
