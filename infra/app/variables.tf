variable "admin_username" {
  description = "Login name of the ADMIN account (not secret). Set in terraform.tfvars (gitignored)."
  type        = string

  validation {
    # Same rules as the backend's AccountRegistry: not empty, no whitespace, '*' is reserved.
    condition     = can(regex("^[^\\s*]+$", var.admin_username))
    error_message = "admin_username must not be empty and must not contain whitespace or '*'."
  }
}
