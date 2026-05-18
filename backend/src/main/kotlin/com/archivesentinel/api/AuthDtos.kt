package com.archivesentinel.api

import jakarta.validation.constraints.NotBlank

data class LoginRequest(@field:NotBlank val username: String, @field:NotBlank val password: String)
data class LoginResponse(val token: String, val username: String)
data class ChangePasswordRequest(@field:NotBlank val currentPassword: String, @field:NotBlank val newPassword: String)
