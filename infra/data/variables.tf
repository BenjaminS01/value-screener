variable "extra_user_count" {
  description = "Number of extra (USER role) accounts besides the admin. Each gets a username and a hash parameter."
  type        = number
  default     = 1

  validation {
    condition     = var.extra_user_count >= 0 && floor(var.extra_user_count) == var.extra_user_count
    error_message = "extra_user_count must be a whole number >= 0."
  }
}
