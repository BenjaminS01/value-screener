variable "github_repository" {
  description = "GitHub repository (owner/name) whose version tags may deploy."
  type        = string
  default     = "BenjaminS01/value-screener"

  validation {
    condition     = can(regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$", var.github_repository))
    error_message = "github_repository must look like owner/name."
  }
}
