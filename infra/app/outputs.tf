output "api_endpoint" {
  description = "Direct HTTP API URL (CloudFront's origin). Direct calls must get 403 from the backend."
  value       = aws_apigatewayv2_api.main.api_endpoint
}

output "frontend_bucket_name" {
  description = "S3 bucket for frontend/dist (aws s3 sync)."
  value       = aws_s3_bucket.frontend.bucket
}

output "cloudfront_domain" {
  description = "Public address of the app: https://<this>."
  value       = aws_cloudfront_distribution.main.domain_name
}

output "distribution_id" {
  description = "For cache invalidations after a frontend deploy."
  value       = aws_cloudfront_distribution.main.id
}
