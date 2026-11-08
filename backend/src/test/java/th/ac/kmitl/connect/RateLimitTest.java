package th.ac.kmitl.connect;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Runs in its own Spring context with the production limits restored. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.rate-limit.post-create.max=2",
        "app.rate-limit.comment-create.max=20"
})
class RateLimitTest {

    @Autowired private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    private static final String ADMIN_OK = "{\"email\":\"admin@kmitl.ac.th\",\"password\":\"Admin@12345\"}";

    private void register(String email) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\",\"displayName\":\"ทดสอบ\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void otpGuessingStopsAfterFiveWrongCodes() throws Exception {
        register("guess@kmitl.ac.th");
        String wrong = "{\"email\":\"guess@kmitl.ac.th\",\"code\":\"000000\"}";
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/verify").contentType(MediaType.APPLICATION_JSON).content(wrong))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/auth/verify").contentType(MediaType.APPLICATION_JSON).content(wrong))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void otpEmailsAreLimitedPerAddress() throws Exception {
        register("resend@kmitl.ac.th");                     // 1st email
        String body = "{\"email\":\"resend@kmitl.ac.th\"}";
        for (int i = 0; i < 2; i++) {                        // 2nd and 3rd
            mvc.perform(post("/api/auth/resend-otp").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/api/auth/resend-otp").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void loginLocksAfterFiveWrongPasswordsEvenWithTheRightOne() throws Exception {
        String bad = "{\"email\":\"locked@kmitl.ac.th\",\"password\":\"wrong-password\"}";
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(bad))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void successfulLoginClearsEarlierFailures() throws Exception {
        String bad = "{\"email\":\"admin@kmitl.ac.th\",\"password\":\"wrong-password\"}";
        for (int i = 0; i < 4; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(bad))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(ADMIN_OK))
                .andExpect(status().isOk());
        for (int i = 0; i < 4; i++) {   // counter starts again from zero
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(bad))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(ADMIN_OK))
                .andExpect(status().isOk());
    }

    @Test
    void postCreationIsLimitedPerUser() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(ADMIN_OK))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession s = (MockHttpSession) r.getRequest().getSession(false);
        long boardId = json.readTree(mvc.perform(get("/api/boards")).andReturn().getResponse().getContentAsString())
                .get(0).get("id").asLong();
        String body = "{\"title\":\"สแปม\",\"content\":\"ทดสอบจำกัดจำนวน\"}";
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/boards/" + boardId + "/posts").session(s)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/api/boards/" + boardId + "/posts").session(s)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests());
    }
}
