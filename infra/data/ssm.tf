locals {
  # Names the backend reads at startup (application-aws.yml: import prefix /value-screener/, the rest is the
  # Spring property name). Both parameters of a user index must exist: a list comes from one property source.
  # The research sync token hash (SHA-256 of the token from MachineTokenGenerator) must hold the real value before
  # a backend with APP_RESEARCH_SYNC_ENABLED=true is published, otherwise the backend refuses to start.
  user_supplied_parameters = concat(
    ["/value-screener/app.admin.password-hash", "/value-screener/app.research-sync.token-hash"],
    flatten([for n in range(var.extra_user_count) : [
      "/value-screener/app.users.${n}.username",
      "/value-screener/app.users.${n}.password-hash",
    ]]),
  )
}

# Secrets the user enters once in the AWS console. Terraform only creates them with a placeholder.
# value_wo is write-only: Terraform writes it once (value_wo_version 1) and never stores the value in state,
# not even the real value it reads back on refresh. Console edits are therefore never overwritten.
resource "aws_ssm_parameter" "user_supplied" {
  for_each = toset(local.user_supplied_parameters)

  name             = each.value
  type             = "SecureString"
  tier             = "Standard"
  value_wo         = "CHANGE_ME"
  value_wo_version = 1
  description      = "Set the real value in the AWS console. Placeholder from Terraform: CHANGE_ME."
}
