# app finds what foundation and data created by their fixed names, not by reading their state files.
data "aws_caller_identity" "current" {}

locals {
  account_id           = data.aws_caller_identity.current.account_id
  artifact_bucket_name = "value-screener-artifacts-${local.account_id}"
}
