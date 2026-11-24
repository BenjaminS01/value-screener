# HTTP API in front of the backend alias. Only CloudFront should call it: the backend rejects every request without
# the X-Origin-Verify header with 403 (Task 7 adds the header in CloudFront).
#
# Emergency switch against a request flood on this URL (costs even for 403/429 responses):
#   terraform destroy -target=aws_apigatewayv2_api.main
# stops all API Gateway and Lambda charges at once; a later apply creates the API with a new, unknown URL
# (CloudFront must then be applied too, it points at the API endpoint).
resource "aws_apigatewayv2_api" "main" {
  name          = "value-screener"
  protocol_type = "HTTP"
}

resource "aws_apigatewayv2_integration" "backend" {
  api_id           = aws_apigatewayv2_api.main.id
  integration_type = "AWS_PROXY"
  integration_uri  = aws_lambda_alias.backend_live.arn

  # 1.0, not 2.0: format 2.0 reads response headers only from a single-value map, where the Java adapter keeps
  # one value per name, so of two Set-Cookie headers (session + CSRF on login) the session cookie was lost.
  # Must match StreamLambdaHandler (getAwsProxyHandler).
  payload_format_version = "1.0"
}

resource "aws_apigatewayv2_route" "api" {
  api_id    = aws_apigatewayv2_api.main.id
  route_key = "ANY /api/{proxy+}"
  target    = "integrations/${aws_apigatewayv2_integration.backend.id}"
}

resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.main.id
  name        = "$default"
  auto_deploy = true

  # User decision 2026-11-23: enough for a few users; a month-long flood costs ~17 $ instead of ~35 $ at 10/20.
  default_route_settings {
    throttling_rate_limit  = 5
    throttling_burst_limit = 10
  }
}

# Lets API Gateway invoke the alias live, and only from this API.
resource "aws_lambda_permission" "api" {
  statement_id  = "AllowHttpApi"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.backend.function_name
  qualifier     = aws_lambda_alias.backend_live.name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.main.execution_arn}/*/*"
}
