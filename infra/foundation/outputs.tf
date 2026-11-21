output "artifact_bucket_name" {
  description = "Name of the S3 bucket for Lambda zips."
  value       = aws_s3_bucket.artifacts.bucket
}

output "github_deploy_role_arn" {
  description = "Role ARN for aws-actions/configure-aws-credentials in the deploy workflow."
  value       = aws_iam_role.github_deploy.arn
}

output "backend_role_name" {
  description = "Name of the backend Lambda role; part 3 attaches its permissions."
  value       = aws_iam_role.backend.name
}

output "backend_role_arn" {
  description = "ARN of the backend Lambda role; part 3 uses it for the Lambda function."
  value       = aws_iam_role.backend.arn
}
