# Runs without AWS: the provider is mocked, nothing is created.
mock_provider "aws" {
  mock_data "aws_caller_identity" {
    defaults = {
      account_id = "123456789012"
    }
  }
}

run "artifact_bucket_is_private_and_cleans_up_old_versions" {
  command = plan

  assert {
    condition     = aws_s3_bucket.artifacts.bucket == "value-screener-artifacts-123456789012"
    error_message = "Bucket name must be value-screener-artifacts-<account id>."
  }

  assert {
    condition     = aws_s3_bucket_versioning.artifacts.versioning_configuration[0].status == "Enabled"
    error_message = "Versioning must be enabled."
  }

  assert {
    condition = (
      one(aws_s3_bucket_server_side_encryption_configuration.artifacts.rule)
      .apply_server_side_encryption_by_default[0].sse_algorithm == "AES256"
    )
    error_message = "Server-side encryption must be SSE-S3 (AES256)."
  }

  assert {
    condition = alltrue([
      aws_s3_bucket_public_access_block.artifacts.block_public_acls,
      aws_s3_bucket_public_access_block.artifacts.block_public_policy,
      aws_s3_bucket_public_access_block.artifacts.ignore_public_acls,
      aws_s3_bucket_public_access_block.artifacts.restrict_public_buckets,
    ])
    error_message = "All four public access blocks must be on."
  }

  assert {
    condition = jsondecode(aws_s3_bucket_policy.artifacts.policy).Statement[0] == {
      Sid       = "DenyInsecureTransport"
      Effect    = "Deny"
      Principal = "*"
      Action    = "s3:*"
      Resource = [
        "arn:aws:s3:::value-screener-artifacts-123456789012",
        "arn:aws:s3:::value-screener-artifacts-123456789012/*",
      ]
      Condition = { Bool = { "aws:SecureTransport" = "false" } }
    }
    error_message = "Bucket policy must deny every request without TLS."
  }

  assert {
    condition = (
      aws_s3_bucket_lifecycle_configuration.artifacts.rule[0].status == "Enabled" &&
      aws_s3_bucket_lifecycle_configuration.artifacts.rule[0].noncurrent_version_expiration[0].noncurrent_days == 30 &&
      aws_s3_bucket_lifecycle_configuration.artifacts.rule[0].abort_incomplete_multipart_upload[0].days_after_initiation == 7
    )
    error_message = "Old versions must expire after 30 days, unfinished uploads after 7 days."
  }

  assert {
    condition     = output.artifact_bucket_name == "value-screener-artifacts-123456789012"
    error_message = "Output artifact_bucket_name must return the bucket name."
  }
}
