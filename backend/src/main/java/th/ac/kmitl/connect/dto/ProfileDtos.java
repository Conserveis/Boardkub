package th.ac.kmitl.connect.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public class ProfileDtos {

    /**
     * A member's public profile. email is filled in only when people look at their
     * own profile, so addresses are never shown to other members.
     */
    public record ProfileResponse(Long id, String displayName, String role, Instant joinedAt,
                                  long postCount, long commentCount, long totalViews,
                                  boolean me, String email) {}

    public record UpdateProfileRequest(@NotBlank @Size(min = 2, max = 60) String displayName) {}

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 72) String newPassword) {}
}
