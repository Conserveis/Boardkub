package th.ac.kmitl.connect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PostImageApiTest {

    @Autowired private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    private MockHttpSession loginAsAdmin() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@kmitl.ac.th\",\"password\":\"Admin@12345\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) r.getRequest().getSession(false);
    }

    private long createPost(MockHttpSession s) throws Exception {
        JsonNode boards = json.readTree(mvc.perform(get("/api/boards")).andReturn().getResponse().getContentAsString());
        long boardId = boards.get(0).get("id").asLong();
        String res = mvc.perform(post("/api/boards/" + boardId + "/posts").session(s)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"มีรูป\",\"content\":\"ทดสอบแนบรูป\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("id").asLong();
    }

    private static MockMultipartFile png() throws Exception {
        return new MockMultipartFile("file", "photo.png", "image/png",
                ImageProcessorTest.image(120, 80, "png", BufferedImage.TYPE_INT_RGB));
    }

    @Test
    void uploadWithoutLoginIsRejected() throws Exception {
        mvc.perform(multipart("/api/posts/1/images").file(png()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadShowDeleteRoundTrip() throws Exception {
        MockHttpSession s = loginAsAdmin();
        long postId = createPost(s);

        String up = mvc.perform(multipart("/api/posts/" + postId + "/images").file(png()).session(s))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String url = json.readTree(up).get("url").asText();
        assertTrue(url.startsWith("/api/media/") && url.endsWith(".jpg"), url);

        JsonNode detail = json.readTree(mvc.perform(get("/api/posts/" + postId))
                .andReturn().getResponse().getContentAsString());
        assertEquals(1, detail.get("images").size());

        mvc.perform(get(url)).andExpect(status().isOk());

        mvc.perform(delete("/api/posts/" + postId).session(s)).andExpect(status().isOk());
        mvc.perform(get(url)).andExpect(status().isNotFound());   // file removed with the post
    }

    @Test
    void atMostFourImagesPerPost() throws Exception {
        MockHttpSession s = loginAsAdmin();
        long postId = createPost(s);
        for (int i = 0; i < 4; i++) {
            mvc.perform(multipart("/api/posts/" + postId + "/images").file(png()).session(s))
                    .andExpect(status().isOk());
        }
        mvc.perform(multipart("/api/posts/" + postId + "/images").file(png()).session(s))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonImageUploadIsRejected() throws Exception {
        MockHttpSession s = loginAsAdmin();
        long postId = createPost(s);
        MockMultipartFile fake = new MockMultipartFile("file", "x.jpg", "image/jpeg", "not an image".getBytes());
        mvc.perform(multipart("/api/posts/" + postId + "/images").file(fake).session(s))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pathTraversalInMediaUrlIsNotServed() throws Exception {
        mvc.perform(get("/api/media/..%2F..%2Fetc%2Fpasswd")).andExpect(status().isNotFound());
    }
}
