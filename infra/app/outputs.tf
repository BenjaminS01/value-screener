output "api_endpoint" {
  description = "Direct HTTP API URL (CloudFront's origin). Direct calls must get 403 from the backend."
  value       = aws_apigatewayv2_api.main.api_endpoint
}
