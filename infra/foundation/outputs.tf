output "artifact_bucket_name" {
  description = "Name of the S3 bucket for Lambda zips."
  value       = aws_s3_bucket.artifacts.bucket
}
