terraform {
  required_version = ">= 1.15"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.23"
    }
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
