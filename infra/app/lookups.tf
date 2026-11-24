# app finds what foundation and data created by their fixed names, not by reading their state files.
data "aws_caller_identity" "current" {}

# Created empty in foundation; app attaches the permissions and uses it for the backend Lambda.
data "aws_iam_role" "backend" {
  name = "value-screener-backend"
}

# Created in data (protected); app only reads and writes items.
data "aws_dynamodb_table" "main" {
  name = "value-screener"
}

locals {
  region               = "eu-central-1"
  account_id           = data.aws_caller_identity.current.account_id
  artifact_bucket_name = "value-screener-artifacts-${local.account_id}"
}
