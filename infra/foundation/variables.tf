variable "alert_email" {
  description = "E-mail address for budget alerts. Set in terraform.tfvars (gitignored), never in the repo."
  type        = string

  validation {
    condition     = can(regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", var.alert_email))
    error_message = "alert_email must be an e-mail address."
  }
}

variable "github_repository" {
  # GitHub's OIDC subject names the repository with ids: repo:<owner>@<owner id>/<name>@<repo id>:ref:...
  # (seen in CloudTrail on 2026-11-24). The ids also stop a re-created repo with the same name from deploying.
  description = "GitHub repository as it appears in the OIDC subject: owner@ownerId/name@repoId."
  type        = string
  default     = "BenjaminS01@56607010/value-screener@1307944384"

  validation {
    condition     = can(regex("^[A-Za-z0-9_.-]+@[0-9]+/[A-Za-z0-9_.-]+@[0-9]+$", var.github_repository))
    error_message = "github_repository must look like owner@ownerId/name@repoId."
  }
}
