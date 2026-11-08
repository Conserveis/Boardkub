package th.ac.kmitl.connect.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthDtos {

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 190) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(min = 2, max = 60) String displayName) {}

    public record VerifyOtpRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 6, max = 6) String code) {}

    public record ResendOtpRequest(@NotBlank @Email String email) {}

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {}

    public record MeResponse(Long id, String email, String displayName, String role) {}
}
