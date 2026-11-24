terraform {
  required_version = ">= 1.15"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.23"
    }
  }

  # The bucket name comes from backend.hcl (gitignored, contains the account id):
  #   terraform init -backend-config=backend.hcl
  backend "s3" {
    key          = "data/terraform.tfstate"
    region       = "eu-central-1"
    encrypt      = true
    use_lockfile = true
  }
}

provider "aws" {
  region = "eu-central-1"

  default_tags {
    tags = {
      project = "value-screener"
    }
  }
}
