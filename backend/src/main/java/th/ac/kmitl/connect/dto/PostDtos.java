package th.ac.kmitl.connect.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public class PostDtos {

    public record PostRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 20000) String content) {}

    public record PostSummary(Long id, String title, String authorName, Long authorId,
                              long viewCount, long commentCount,
                              Instant createdAt, Long boardId, String boardName) {}

    public record PostDetail(Long id, Long boardId, String boardName, Long categoryId, String categoryName,
                             String title, String content, String authorName, Long authorId,
                             long viewCount, Instant createdAt, Instant updatedAt,
                             boolean canEdit, List<ImageResponse> images) {}

    /** url points at Object Storage (or /api/media/ when running locally). */
    public record ImageResponse(Long id, String url, int width, int height) {}

    public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {}

    public record CommentRequest(@NotBlank @Size(max = 5000) String content) {}

    public record CommentResponse(Long id, Long postId, String content, String authorName,
                                  Long authorId, String authorRole, Instant createdAt, Instant updatedAt,
                                  boolean canEdit) {}
}
