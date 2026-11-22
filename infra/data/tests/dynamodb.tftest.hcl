# Runs without AWS: the provider is mocked, nothing is created.
mock_provider "aws" {}

run "table_matches_the_backend_and_is_protected" {
  command = plan

  assert {
    condition     = aws_dynamodb_table.main.name == "value-screener"
    error_message = "Table name must be value-screener (backend default APP_DYNAMODB_TABLE_NAME)."
  }

  assert {
    condition = (
      aws_dynamodb_table.main.billing_mode == "PROVISIONED"
      && aws_dynamodb_table.main.read_capacity == 20
      && aws_dynamodb_table.main.write_capacity == 20
    )
    error_message = "Table must be provisioned with 20 RCU / 20 WCU (free tier, TableDefinition.java)."
  }

  assert {
    condition     = aws_dynamodb_table.main.hash_key == "pk" && aws_dynamodb_table.main.range_key == "sk"
    error_message = "Keys must be pk (hash) and sk (range)."
  }

  assert {
    condition = (
      length(aws_dynamodb_table.main.attribute) == 2
      && alltrue([for a in aws_dynamodb_table.main.attribute : a.type == "S"])
    )
    error_message = "Only pk and sk are declared, both as String."
  }

  assert {
    condition = (
      aws_dynamodb_table.main.ttl[0].enabled
      && aws_dynamodb_table.main.ttl[0].attribute_name == "ttl"
    )
    error_message = "TTL must be enabled on the attribute ttl."
  }

  assert {
    condition     = aws_dynamodb_table.main.point_in_time_recovery[0].enabled
    error_message = "Point-in-time recovery must be enabled."
  }

  assert {
    condition     = aws_dynamodb_table.main.deletion_protection_enabled
    error_message = "Deletion protection must be enabled."
  }
}
