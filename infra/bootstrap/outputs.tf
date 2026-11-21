output "state_bucket_name" {
  description = "Name of the S3 bucket that holds the Terraform state of the other stacks."
  value       = aws_s3_bucket.state.bucket
}
