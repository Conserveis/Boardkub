package th.ac.kmitl.connect;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void registeringTwiceWithTheSameEmailIsRejected() throws Exception {
        String body = "{\"email\":\"dup@kmitl.ac.th\",\"password\":\"password123\",\"displayName\":\"ทดสอบ\"}";

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void loginBeforeVerifyingTheEmailIsRejected() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"unverified@kmitl.ac.th\",\"password\":\"password123\",\"displayName\":\"ยังไม่ยืนยัน\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"unverified@kmitl.ac.th\",\"password\":\"password123\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aShortPasswordIsRejected() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"short@kmitl.ac.th\",\"password\":\"123\",\"displayName\":\"สั้น\"}"))
                .andExpect(status().isBadRequest());
    }
}
