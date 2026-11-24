# Runs without AWS: the provider is mocked, nothing is created.
mock_provider "aws" {}

run "one_extra_user_gives_admin_hash_and_both_user_parameters" {
  command = plan

  variables {
    extra_user_count = 1
  }

  assert {
    condition = toset(keys(aws_ssm_parameter.user_supplied)) == toset([
      "/value-screener/app.admin.password-hash",
      "/value-screener/app.users.0.username",
      "/value-screener/app.users.0.password-hash",
      "/value-screener/app.research-sync.token-hash",
    ])
    error_message = "Expected the admin hash, username and hash of user 0 and the sync token hash (names from application-aws.yml / MachineTokenProperties)."
  }

  assert {
    condition     = alltrue([for p in aws_ssm_parameter.user_supplied : p.type == "SecureString" && p.tier == "Standard"])
    error_message = "All parameters must be SecureString in the free Standard tier."
  }

  assert {
    condition     = alltrue([for k, p in aws_ssm_parameter.user_supplied : p.name == k])
    error_message = "Each parameter's name must be its map key."
  }

  assert {
    # value_wo is write-only (never in plan or state), so it cannot be asserted; the provider allows exactly one of
    # value / insecure_value / value_wo, so a set value_wo_version means value_wo is used. Fixed at 1: written once.
    condition     = alltrue([for p in aws_ssm_parameter.user_supplied : p.value_wo_version == 1])
    error_message = "The placeholder must be written via value_wo (version 1), never via value (would land in state)."
  }
}

run "no_extra_user_gives_admin_and_sync_hash" {
  command = plan

  variables {
    extra_user_count = 0
  }

  assert {
    condition = toset(keys(aws_ssm_parameter.user_supplied)) == toset([
      "/value-screener/app.admin.password-hash",
      "/value-screener/app.research-sync.token-hash",
    ])
    error_message = "Without extra users only the admin hash and the sync token hash are created."
  }
}

run "negative_user_count_is_rejected" {
  command = plan

  variables {
    extra_user_count = -1
  }

  expect_failures = [var.extra_user_count]
}
