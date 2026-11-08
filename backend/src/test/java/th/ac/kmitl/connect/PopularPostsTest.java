package th.ac.kmitl.connect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PopularPostsTest {

    @Autowired private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void mostViewedPostComesFirstAndListHasAtMostFive() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@kmitl.ac.th\",\"password\":\"Admin@12345\"}"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession s = (MockHttpSession) r.getRequest().getSession(false);
        long boardId = json.readTree(mvc.perform(get("/api/boards")).andReturn().getResponse().getContentAsString())
                .get(0).get("id").asLong();

        long hot = 0;
        for (int i = 0; i < 6; i++) {
            String res = mvc.perform(post("/api/boards/" + boardId + "/posts").session(s)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"กระทู้ที่ " + i + "\",\"content\":\"ทดสอบยอดนิยม\"}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            if (i == 3) hot = json.readTree(res).get("id").asLong();
        }
        for (int i = 0; i < 40; i++) {           // opening a post counts one view
            mvc.perform(get("/api/posts/" + hot)).andExpect(status().isOk());
        }

        JsonNode top = json.readTree(mvc.perform(get("/api/posts/popular"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertTrue(top.size() <= 5);
        assertEquals(hot, top.get(0).get("id").asLong());
        for (int i = 1; i < top.size(); i++) {
            assertTrue(top.get(i - 1).get("viewCount").asLong() >= top.get(i).get("viewCount").asLong());
        }
    }
}
