variable "alert_email" {
  description = "E-mail address for budget alerts. Set in terraform.tfvars (gitignored), never in the repo."
  type        = string

  validation {
    condition     = can(regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", var.alert_email))
    error_message = "alert_email must be an e-mail address."
  }
}

variable "github_repository" {
  description = "GitHub repository (owner/name) whose version tags may deploy."
  type        = string
  default     = "BenjaminS01/value-screener"

  validation {
    condition     = can(regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$", var.github_repository))
    error_message = "github_repository must look like owner/name."
  }
}
