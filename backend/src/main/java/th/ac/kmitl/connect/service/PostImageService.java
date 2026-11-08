package th.ac.kmitl.connect.service;

import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import th.ac.kmitl.connect.dto.PostDtos;
import th.ac.kmitl.connect.entity.Post;
import th.ac.kmitl.connect.entity.PostImage;
import th.ac.kmitl.connect.exception.ApiException;
import th.ac.kmitl.connect.image.ImageProcessor;
import th.ac.kmitl.connect.repository.PostImageRepository;
import th.ac.kmitl.connect.repository.PostRepository;
import th.ac.kmitl.connect.storage.MediaStorage;
import th.ac.kmitl.connect.storage.ObjectNames;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Service
public class PostImageService {

    public static final int MAX_IMAGES_PER_POST = 4;
    public static final long MAX_UPLOAD_BYTES = 5L * 1024 * 1024;

    private static final Logger log = LoggerFactory.getLogger(PostImageService.class);

    private final PostImageRepository imageRepository;
    private final PostRepository postRepository;
    private final ImageProcessor processor;
    private final MediaStorage storage;
    private final CurrentUser currentUser;

    public PostImageService(PostImageRepository imageRepository, PostRepository postRepository,
                            ImageProcessor processor, MediaStorage storage, CurrentUser currentUser) {
        this.imageRepository = imageRepository;
        this.postRepository = postRepository;
        this.processor = processor;
        this.storage = storage;
        this.currentUser = currentUser;
    }

    @Transactional
    public PostDtos.ImageResponse upload(Long postId, MultipartFile file, HttpSession session) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> ApiException.notFound("ไม่พบกระทู้ที่ต้องการ"));
        currentUser.requireOwnerOrAdmin(session, post.getUser().getId());

        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("กรุณาเลือกรูปภาพ");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw ApiException.badRequest("รูปต้องมีขนาดไม่เกิน 5 MB");
        }
        long count = imageRepository.countByPostId(postId);
        if (count >= MAX_IMAGES_PER_POST) {
            throw ApiException.badRequest("แนบรูปได้ไม่เกิน " + MAX_IMAGES_PER_POST + " รูปต่อกระทู้");
        }

        ImageProcessor.Result img;
        try {
            img = processor.process(file.getBytes());
        } catch (IOException e) {
            throw ApiException.badRequest("อ่านไฟล์รูปไม่ได้");
        }

        String objectName = ObjectNames.random(img.extension());
        storage.put(objectName, img.bytes(), img.contentType());
        // If the database insert fails, remove the file so storage holds no orphans
        deleteObjectIfRolledBack(objectName);

        PostImage pi = new PostImage();
        pi.setPost(post);
        pi.setObjectName(objectName);
        pi.setContentType(img.contentType());
        pi.setWidth(img.width());
        pi.setHeight(img.height());
        pi.setSizeBytes(img.bytes().length);
        pi.setSortOrder((int) count);
        pi.setCreatedAt(Instant.now());
        imageRepository.save(pi);
        return toResponse(pi);
    }

    @Transactional
    public void delete(Long imageId, HttpSession session) {
        PostImage pi = imageRepository.findById(imageId)
                .orElseThrow(() -> ApiException.notFound("ไม่พบรูปภาพ"));
        currentUser.requireOwnerOrAdmin(session, pi.getPost().getUser().getId());
        imageRepository.delete(pi);
        deleteObjectsAfterCommit(List.of(pi.getObjectName()));
    }

    @Transactional(readOnly = true)
    public List<PostDtos.ImageResponse> listForPost(Long postId) {
        return imageRepository.findByPostIdOrderBySortOrderAscIdAsc(postId).stream()
                .map(this::toResponse)
                .toList();
    }

    /** Called when a post is deleted. Files are removed only after the database commit. */
    @Transactional
    public void deleteAllForPost(Long postId) {
        List<PostImage> images = imageRepository.findByPostIdOrderBySortOrderAscIdAsc(postId);
        if (images.isEmpty()) return;
        imageRepository.deleteAll(images);
        deleteObjectsAfterCommit(images.stream().map(PostImage::getObjectName).toList());
    }

    private PostDtos.ImageResponse toResponse(PostImage pi) {
        return new PostDtos.ImageResponse(pi.getId(), storage.publicUrl(pi.getObjectName()),
                pi.getWidth(), pi.getHeight());
    }

    private void deleteObjectsAfterCommit(List<String> names) {
        Runnable work = () -> names.forEach(this::quietDelete);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { work.run(); }
            });
        } else {
            work.run();
        }
    }

    private void deleteObjectIfRolledBack(String name) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) quietDelete(name);
            }
        });
    }

    private void quietDelete(String name) {
        try {
            storage.delete(name);
        } catch (RuntimeException e) {
            // The row is already gone; a leftover file costs a little storage but no correctness
            log.warn("Could not delete media object {}: {}", name, e.getMessage());
        }
    }
}
