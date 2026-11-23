# Runs without AWS: the providers are mocked, nothing is created.
mock_provider "aws" {
  # Computed values (ARNs, ids, domains) get the mock defaults already during plan, so they can be asserted.
  override_during = plan

  mock_resource "aws_apigatewayv2_api" {
    defaults = {
      execution_arn = "arn:aws:execute-api:eu-central-1:123456789012:abcde12345"
      api_endpoint  = "https://abcde12345.execute-api.eu-central-1.amazonaws.com"
    }
  }

  mock_resource "aws_s3_bucket" {
    defaults = {
      arn                         = "arn:aws:s3:::value-screener-frontend-123456789012"
      bucket_regional_domain_name = "value-screener-frontend-123456789012.s3.eu-central-1.amazonaws.com"
    }
  }

  mock_resource "aws_cloudfront_distribution" {
    defaults = {
      arn = "arn:aws:cloudfront::123456789012:distribution/EDFDVBD6EXAMPLE"
    }
  }

  mock_resource "aws_cloudfront_origin_request_policy" {
    defaults = {
      id = "api-origin-request-policy-id"
    }
  }

  mock_resource "aws_cloudfront_response_headers_policy" {
    defaults = {
      id = "security-headers-policy-id"
    }
  }

  mock_resource "aws_cloudfront_origin_access_control" {
    defaults = {
      id = "frontend-oac-id"
    }
  }

  mock_resource "aws_lambda_alias" {
    defaults = {
      arn = "arn:aws:lambda:eu-central-1:123456789012:function:value-screener-backend:live"
    }
  }

  mock_data "aws_caller_identity" {
    defaults = {
      account_id = "123456789012"
    }
  }

  mock_data "aws_iam_role" {
    defaults = {
      name = "value-screener-backend"
      arn  = "arn:aws:iam::123456789012:role/value-screener-backend"
    }
  }

  mock_data "aws_dynamodb_table" {
    defaults = {
      name = "value-screener"
      arn  = "arn:aws:dynamodb:eu-central-1:123456789012:table/value-screener"
    }
  }
}

mock_provider "random" {
  override_during = plan

  mock_resource "random_password" {
    defaults = {
      result = "test-origin-secret"
    }
  }
}

variables {
  admin_username = "admin-test"
}

run "frontend_bucket_is_private_and_readable_only_by_this_distribution" {
  command = plan

  assert {
    condition     = aws_s3_bucket.frontend.bucket == "value-screener-frontend-123456789012"
    error_message = "Bucket name must be value-screener-frontend-<account id>."
  }

  assert {
    condition = alltrue([
      aws_s3_bucket_public_access_block.frontend.block_public_acls,
      aws_s3_bucket_public_access_block.frontend.block_public_policy,
      aws_s3_bucket_public_access_block.frontend.ignore_public_acls,
      aws_s3_bucket_public_access_block.frontend.restrict_public_buckets,
    ])
    error_message = "All four public access blocks must be on."
  }

  assert {
    condition = (
      aws_s3_bucket_versioning.frontend.versioning_configuration[0].status == "Enabled"
      && one(aws_s3_bucket_server_side_encryption_configuration.frontend.rule)
      .apply_server_side_encryption_by_default[0].sse_algorithm == "AES256"
    )
    error_message = "Versioning and SSE-S3 like every bucket in this project."
  }

  assert {
    condition = {
      for s in jsondecode(aws_s3_bucket_policy.frontend.policy).Statement : s.Sid => s
      }["CloudFrontReadsThroughOac"] == {
      Sid       = "CloudFrontReadsThroughOac"
      Effect    = "Allow"
      Principal = { Service = "cloudfront.amazonaws.com" }
      Action    = "s3:GetObject"
      Resource  = "arn:aws:s3:::value-screener-frontend-123456789012/*"
      Condition = { StringEquals = { "AWS:SourceArn" = "arn:aws:cloudfront::123456789012:distribution/EDFDVBD6EXAMPLE" } }
    }
    error_message = "Only this distribution may read objects (OAC); nobody else."
  }

  assert {
    condition = contains(
      [for s in jsondecode(aws_s3_bucket_policy.frontend.policy).Statement : s.Sid], "DenyInsecureTransport"
    )
    error_message = "Bucket policy must deny non-TLS access."
  }
}

run "api_behaviour_forwards_session_csrf_and_viewer_ip_and_adds_the_origin_secret" {
  command = plan

  assert {
    condition = {
      for o in aws_cloudfront_distribution.main.origin : o.origin_id => o.domain_name
      } == {
      frontend = "value-screener-frontend-123456789012.s3.eu-central-1.amazonaws.com"
      api      = "abcde12345.execute-api.eu-central-1.amazonaws.com"
    }
    error_message = "Two origins: the S3 bucket and the HTTP API (host without https://)."
  }

  assert {
    condition = [
      for o in aws_cloudfront_distribution.main.origin : one(o.custom_header).value
      if o.origin_id == "api" && one(o.custom_header).name == "X-Origin-Verify"
    ] == [random_password.origin_verify.result]
    error_message = "CloudFront must send the origin secret as X-Origin-Verify to the API (overwrites a viewer's value)."
  }

  assert {
    condition = [
      for o in aws_cloudfront_distribution.main.origin : one(o.custom_origin_config).origin_protocol_policy
      if o.origin_id == "api"
    ] == ["https-only"]
    error_message = "CloudFront talks to the API over HTTPS only."
  }

  assert {
    condition = (
      length(aws_cloudfront_distribution.main.ordered_cache_behavior) == 1
      && aws_cloudfront_distribution.main.ordered_cache_behavior[0].path_pattern == "/api/*"
      && aws_cloudfront_distribution.main.ordered_cache_behavior[0].target_origin_id == "api"
      && toset(aws_cloudfront_distribution.main.ordered_cache_behavior[0].allowed_methods) == toset(["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"])
      && aws_cloudfront_distribution.main.ordered_cache_behavior[0].cache_policy_id == "4135ea2d-6df8-44a3-9df3-4b5a84be39ad"
      && aws_cloudfront_distribution.main.ordered_cache_behavior[0].origin_request_policy_id == "api-origin-request-policy-id"
      && aws_cloudfront_distribution.main.ordered_cache_behavior[0].viewer_protocol_policy == "https-only"
    )
    error_message = "/api/* → API: all methods, never cached, own origin request policy, HTTPS only."
  }

  assert {
    condition = (
      aws_cloudfront_origin_request_policy.api.cookies_config[0].cookie_behavior == "all"
      && aws_cloudfront_origin_request_policy.api.query_strings_config[0].query_string_behavior == "all"
      && aws_cloudfront_origin_request_policy.api.headers_config[0].header_behavior == "whitelist"
      && toset(aws_cloudfront_origin_request_policy.api.headers_config[0].headers[0].items) == toset(["Accept", "Content-Type", "X-XSRF-TOKEN", "CloudFront-Viewer-Address"])
    )
    error_message = "Forward all cookies (session, XSRF-TOKEN) and query strings; headers exactly Accept, Content-Type, X-XSRF-TOKEN, CloudFront-Viewer-Address (never Host)."
  }
}

run "default_behaviour_serves_the_spa_from_s3_without_error_rewrites" {
  command = plan

  assert {
    condition = (
      aws_cloudfront_distribution.main.default_cache_behavior[0].target_origin_id == "frontend"
      && aws_cloudfront_distribution.main.default_cache_behavior[0].cache_policy_id == "658327ea-f89d-4fab-a63d-7e88639e58f6"
      && aws_cloudfront_distribution.main.default_cache_behavior[0].viewer_protocol_policy == "redirect-to-https"
      && aws_cloudfront_distribution.main.default_root_object == "index.html"
    )
    error_message = "Default → S3 via OAC, cached, HTTP redirected to HTTPS, / serves index.html."
  }

  assert {
    condition = (
      length(aws_cloudfront_distribution.main.origin[*].s3_origin_config) > 0
      && [for o in aws_cloudfront_distribution.main.origin : o.origin_access_control_id if o.origin_id == "frontend"] == ["frontend-oac-id"]
    )
    error_message = "The S3 origin is read through the origin access control."
  }

  assert {
    condition     = length(aws_cloudfront_distribution.main.custom_error_response) == 0
    error_message = "No SPA fallback: the app has no router, and a 403/404 rewrite would turn API errors into index.html."
  }

  assert {
    condition     = aws_cloudfront_distribution.main.price_class == "PriceClass_100"
    error_message = "Cheapest price class (Europe and North America edges)."
  }
}

run "every_response_gets_the_security_headers" {
  command = plan

  assert {
    condition = (
      aws_cloudfront_distribution.main.default_cache_behavior[0].response_headers_policy_id == "security-headers-policy-id"
      && aws_cloudfront_distribution.main.ordered_cache_behavior[0].response_headers_policy_id == "security-headers-policy-id"
    )
    error_message = "Both behaviours use the security headers policy."
  }

  assert {
    condition = (
      aws_cloudfront_response_headers_policy.security.security_headers_config[0].strict_transport_security[0].access_control_max_age_sec == 31536000
      && aws_cloudfront_response_headers_policy.security.security_headers_config[0].content_type_options[0].override
      && aws_cloudfront_response_headers_policy.security.security_headers_config[0].frame_options[0].frame_option == "DENY"
      && aws_cloudfront_response_headers_policy.security.security_headers_config[0].referrer_policy[0].referrer_policy == "same-origin"
    )
    error_message = "HSTS one year, nosniff, frames denied, referrer same-origin."
  }

  assert {
    condition = (
      aws_cloudfront_response_headers_policy.security.security_headers_config[0].content_security_policy[0].content_security_policy
      == "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; connect-src 'self'; font-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'"
    )
    error_message = "Strict CSP: the built app loads one module script from /assets and nothing inline or external."
  }
}
