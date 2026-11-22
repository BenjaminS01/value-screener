output "table_name" {
  description = "Name of the DynamoDB table (Lambda env APP_DYNAMODB_TABLE_NAME)."
  value       = aws_dynamodb_table.main.name
}

output "table_arn" {
  description = "ARN of the DynamoDB table (backend role permissions in app)."
  value       = aws_dynamodb_table.main.arn
}
