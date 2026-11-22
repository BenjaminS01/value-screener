# The one table of the backend (single-table design: pk/sk, items expire via ttl).
# Must match backend/.../persistence/TableDefinition.java; TableDefinitionTerraformTest compares both.
resource "aws_dynamodb_table" "main" {
  name           = "value-screener"
  billing_mode   = "PROVISIONED"
  read_capacity  = 20
  write_capacity = 20
  hash_key       = "pk"
  range_key      = "sk"

  attribute {
    name = "pk"
    type = "S"
  }

  attribute {
    name = "sk"
    type = "S"
  }

  ttl {
    attribute_name = "ttl"
    enabled        = true
  }

  point_in_time_recovery {
    enabled = true
  }

  # Two locks against losing data: AWS refuses DeleteTable, Terraform refuses to plan a destroy.
  deletion_protection_enabled = true

  lifecycle {
    prevent_destroy = true
  }
}
