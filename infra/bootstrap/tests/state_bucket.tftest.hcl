# Runs without AWS: the provider is mocked, nothing is created.
mock_provider "aws" {
  mock_data "aws_caller_identity" {
    defaults = {
      account_id = "123456789012"
    }
  }
}

run "state_bucket_is_locked_down" {
  command = plan

  assert {
    condition     = aws_s3_bucket.state.bucket == "value-screener-tfstate-123456789012"
    error_message = "Bucket name must be value-screener-tfstate-<account id>."
  }

  assert {
    condition     = aws_s3_bucket_versioning.state.versioning_configuration[0].status == "Enabled"
    error_message = "Versioning must be enabled."
  }

  assert {
    condition = (
      one(aws_s3_bucket_server_side_encryption_configuration.state.rule)
      .apply_server_side_encryption_by_default[0].sse_algorithm == "AES256"
    )
    error_message = "Server-side encryption must be SSE-S3 (AES256)."
  }

  assert {
    condition = alltrue([
      aws_s3_bucket_public_access_block.state.block_public_acls,
      aws_s3_bucket_public_access_block.state.block_public_policy,
      aws_s3_bucket_public_access_block.state.ignore_public_acls,
      aws_s3_bucket_public_access_block.state.restrict_public_buckets,
    ])
    error_message = "All four public access blocks must be on."
  }

  assert {
    condition = jsondecode(aws_s3_bucket_policy.state.policy).Statement[0] == {
      Sid       = "DenyInsecureTransport"
      Effect    = "Deny"
      Principal = "*"
      Action    = "s3:*"
      Resource = [
        "arn:aws:s3:::value-screener-tfstate-123456789012",
        "arn:aws:s3:::value-screener-tfstate-123456789012/*",
      ]
      Condition = { Bool = { "aws:SecureTransport" = "false" } }
    }
    error_message = "Bucket policy must deny every request without TLS."
  }

  assert {
    condition     = output.state_bucket_name == "value-screener-tfstate-123456789012"
    error_message = "Output state_bucket_name must return the bucket name."
  }
}
