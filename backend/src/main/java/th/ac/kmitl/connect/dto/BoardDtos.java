package th.ac.kmitl.connect.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class BoardDtos {

    public record CategoryResponse(Long id, String name, String description, int displayOrder, long postCount) {}

    public record CategoryRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 255) String description,
            int displayOrder) {}

    public record BoardResponse(Long id, Long categoryId, String categoryName,
                                String name, String description, long postCount) {}

    public record BoardRequest(
            @NotNull Long categoryId,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 255) String description) {}
}
